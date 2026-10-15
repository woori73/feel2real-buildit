package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * TESTPREP 2단계 — 응시·답안·제출·채점·결과·오답 전 과정 MySQL 통합 테스트.
 *
 * <p>실제 V1076(스키마)+V1077(콘텐츠)가 Flyway로 적용된 DB에서, 기존 feel2real SSO(JWT ACCESS_TOKEN 쿠키)를
 * 재사용해 다음 불변식을 확인한다:
 * <ul>
 *   <li>목록 → 응시 시작 → 답안 저장 → 제출(자동채점) → 결과 조회 → 오답 조회가 순서대로 동작한다.</li>
 *   <li>미로그인 요청은 401로 차단된다.</li>
 *   <li>타인의 응시 기록은 존재를 노출하지 않고 404로 차단된다.</li>
 * </ul>
 * tp_attempts.user_id에는 FK가 없으므로 실제 User 행 없이 합성 userId로 응시자를 표현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("TESTPREP 2단계 MySQL 통합 — 응시·제출·채점·오답·접근 차단")
class TestPrepFlowMySqlIntegrationTest {

  private static final String API = "/api/testprep";
  private static final String VOCAB_EXAM = "TP-VOCAB-EASY";
  private static final long OWNER_ID = 900101L;
  private static final long OTHER_ID = 900202L;
  private static final String BAND_DISCLAIMER =
      "TestPrep internal band estimate — not an official ETS TOEFL iBT score.";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private JdbcTemplate jdbc;

  private Cookie auth(long userId) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "tp_" + userId, "STUDENT"));
  }

  private JsonNode json(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  private Map<Long, String> answerKeys() {
    Long examId = jdbc.queryForObject(
        "SELECT exam_id FROM tp_exams WHERE exam_code = ?", Long.class, VOCAB_EXAM);
    Map<Long, String> keys = new LinkedHashMap<>();
    jdbc.query(
        "SELECT question_id, answer_key FROM tp_questions WHERE exam_id = ? ORDER BY order_no",
        rs -> { keys.put(rs.getLong(1), rs.getString(2)); return null; },
        examId);
    return keys;
  }

  private long startAttempt(long userId) throws Exception {
    MvcResult r = mockMvc.perform(post(API + "/attempts").cookie(auth(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("examCode", VOCAB_EXAM))))
        .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json(r).get("attemptId").asLong();
  }

  @Test
  @DisplayName("목록→응시→답안저장→제출→결과→오답조회 전체 흐름 (전원 정답)")
  void fullFlow_allCorrect() throws Exception {
    // 1) 시험 목록 (kind=VOCAB)
    MvcResult list = mockMvc.perform(get(API + "/exams").param("kind", "VOCAB").cookie(auth(OWNER_ID)))
        .andReturn();
    assertThat(list.getResponse().getStatus()).isEqualTo(200);
    JsonNode exams = json(list);
    List<String> codes = new ArrayList<>();
    exams.forEach(e -> codes.add(e.get("examCode").asText()));
    assertThat(codes).contains(VOCAB_EXAM);

    // 2) 응시 시작
    MvcResult start = mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("examCode", VOCAB_EXAM))))
        .andReturn();
    assertThat(start.getResponse().getStatus()).isEqualTo(200);
    JsonNode view = json(start);
    long attemptId = view.get("attemptId").asLong();
    assertThat(view.get("examCode").asText()).isEqualTo(VOCAB_EXAM);
    assertThat(view.get("questions")).hasSize(8);
    assertThat(view.get("bandDisclaimer").asText()).isEqualTo(BAND_DISCLAIMER);

    // 3) 답안 저장 — 실제 정답 키로每题 PUT
    Map<Long, String> keys = answerKeys();
    for (Map.Entry<Long, String> en : keys.entrySet()) {
      MvcResult put = mockMvc.perform(put(API + "/attempts/" + attemptId + "/answers").cookie(auth(OWNER_ID))
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(
                  Map.of("questionId", en.getKey(), "answerText", en.getValue()))))
          .andReturn();
      assertThat(put.getResponse().getStatus()).isEqualTo(204);
    }

    // 4~5) 제출 + 자동채점
    MvcResult submit = mockMvc.perform(post(API + "/attempts/" + attemptId + "/submit")
            .cookie(auth(OWNER_ID)).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andReturn();
    assertThat(submit.getResponse().getStatus()).isEqualTo(200);
    JsonNode card = json(submit);
    assertThat(card.get("accuracy").asDouble()).isEqualTo(100.0);
    assertThat(card.get("overallBand").asDouble()).isEqualTo(6.0);
    assertThat(card.get("bandDisclaimer").asText()).isEqualTo(BAND_DISCLAIMER);

    // 6) 결과(오답) 조회 — review
    MvcResult review = mockMvc.perform(get(API + "/attempts/" + attemptId + "/review").cookie(auth(OWNER_ID)))
        .andReturn();
    assertThat(review.getResponse().getStatus()).isEqualTo(200);
    JsonNode items = json(review).get("items");
    assertThat(items).hasSize(8);
    items.forEach(it -> assertThat(it.get("isCorrect").asBoolean()).isTrue());

    // 응시 이력
    MvcResult hist = mockMvc.perform(get(API + "/attempts").cookie(auth(OWNER_ID))).andReturn();
    assertThat(hist.getResponse().getStatus()).isEqualTo(200);
    JsonNode h = json(hist).get(0);
    assertThat(h.get("attemptId").asLong()).isEqualTo(attemptId);
    assertThat(h.get("status").asText()).isEqualTo("SUBMITTED");

    // 7) 오답Bank (learning) — 전원 정답이면 오답 0건
    MvcResult learning = mockMvc.perform(get(API + "/learning").cookie(auth(OWNER_ID))).andReturn();
    assertThat(learning.getResponse().getStatus()).isEqualTo(200);
    JsonNode lv = json(learning);
    assertThat(lv.get("wrongCount").asLong()).isEqualTo(0);
    assertThat(lv.get("bestBand").asDouble()).isEqualTo(6.0);
  }

  @Test
  @DisplayName("전원 오답 → 오답Bank에 8건 적재 및 오답 다시풀기 응시")
  void wrongAnswers_bankAndRetry() throws Exception {
    long attemptId = startAttempt(OWNER_ID);
    Map<Long, String> keys = answerKeys();
    List<Long> ids = new ArrayList<>();
    for (Map.Entry<Long, String> en : keys.entrySet()) {
      ids.add(en.getKey());
      String wrong = "ZZZ"; // 어떤 정답 키와도 다른 값
      MvcResult put = mockMvc.perform(put(API + "/attempts/" + attemptId + "/answers").cookie(auth(OWNER_ID))
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(
                  Map.of("questionId", en.getKey(), "answerText", wrong))))
          .andReturn();
      assertThat(put.getResponse().getStatus()).isEqualTo(204);
    }

    MvcResult submit = mockMvc.perform(post(API + "/attempts/" + attemptId + "/submit")
            .cookie(auth(OWNER_ID)).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andReturn();
    assertThat(submit.getResponse().getStatus()).isEqualTo(200);
    JsonNode card = json(submit);
    assertThat(card.get("accuracy").asDouble()).isEqualTo(0.0);
    assertThat(card.get("overallBand").asDouble()).isEqualTo(1.5);

    MvcResult learning = mockMvc.perform(get(API + "/learning").cookie(auth(OWNER_ID))).andReturn();
    JsonNode lv = json(learning);
    assertThat(lv.get("wrongCount").asLong()).isEqualTo(8);
    assertThat(lv.get("wrongItems")).hasSize(8);

    // 오답 다시풀기 — 오답 questionIds만 넘기면 그 문제만 응시 생성
    MvcResult retry = mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("examCode", VOCAB_EXAM, "questionIds", ids))))
        .andReturn();
    assertThat(retry.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(retry).get("questions")).hasSize(8);
  }

  @Test
  @DisplayName("타인의 응시 기록은 404로 존재 자체를 숨긴다")
  void crossUserAttemptIsNotFound() throws Exception {
    long attemptId = startAttempt(OWNER_ID);

    MvcResult resume = mockMvc.perform(get(API + "/attempts/" + attemptId).cookie(auth(OTHER_ID))).andReturn();
    assertThat(resume.getResponse().getStatus()).isEqualTo(404);

    MvcResult review = mockMvc.perform(
        get(API + "/attempts/" + attemptId + "/review").cookie(auth(OTHER_ID))).andReturn();
    assertThat(review.getResponse().getStatus()).isEqualTo(404);

    MvcResult put = mockMvc.perform(put(API + "/attempts/" + attemptId + "/answers").cookie(auth(OTHER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("questionId", 1L, "answerText", "A"))))
        .andReturn();
    assertThat(put.getResponse().getStatus()).isEqualTo(404);

    // 다른 사용자의 학습 대시보드에는OWNER의 오답이 드러나지 않는다.
    MvcResult otherLearning = mockMvc.perform(get(API + "/learning").cookie(auth(OTHER_ID))).andReturn();
    assertThat(json(otherLearning).get("totalAttempts").asLong()).isEqualTo(0);
  }

  @Test
  @DisplayName("미로그인 요청은 401로 차단된다")
  void unauthenticatedRequestsAreBlocked() throws Exception {
    assertThat(mockMvc.perform(get(API + "/exams").param("kind", "VOCAB"))
        .andReturn().getResponse().getStatus()).isEqualTo(401);
    assertThat(mockMvc.perform(post(API + "/attempts").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("examCode", VOCAB_EXAM))))
        .andReturn().getResponse().getStatus()).isEqualTo(401);
    assertThat(mockMvc.perform(get(API + "/learning"))
        .andReturn().getResponse().getStatus()).isEqualTo(401);
  }
}
