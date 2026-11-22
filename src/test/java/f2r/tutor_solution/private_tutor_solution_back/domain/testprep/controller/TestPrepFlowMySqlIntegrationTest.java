package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
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
  private static final String SAT_EXAM = "TP-SAT-MINI-01";
  private static final String SAT_DISCLAIMER =
      "F2R internal score estimate — not an official College Board SAT score.";

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
        (java.sql.ResultSet rs) -> { keys.put(rs.getLong("question_id"), rs.getString("answer_key")); },
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
  @Test
  @DisplayName("SAT 미니 세트: 전원 정답(주관식은 다른 꼴의 같은 값)이면 영역 800·합계 1600, SAT 안내 문구")
  void satFlow_allCorrectWithEquivalentSpr() throws Exception {
    // given — kind=SAT 목록에 미니 세트가 있다
    MvcResult list = mockMvc.perform(get(API + "/exams").param("kind", "SAT").cookie(auth(OWNER_ID))).andReturn();
    List<String> codes = new ArrayList<>();
    json(list).forEach(e -> codes.add(e.get("examCode").asText()));
    assertThat(codes).contains(SAT_EXAM);

    MvcResult start = mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("examCode", SAT_EXAM))))
        .andReturn();
    JsonNode view = json(start);
    long attemptId = view.get("attemptId").asLong();
    assertThat(view.get("kind").asText()).isEqualTo("SAT");
    assertThat(view.get("questions")).hasSize(18);
    assertThat(view.get("bandDisclaimer").asText()).isEqualTo(SAT_DISCLAIMER);
    view.get("questions").forEach(q -> {
      JsonNode help;
      try { help = objectMapper.readTree(q.get("koHelpJson").asText()); } catch (Exception e) { throw new AssertionError(e); }
      assertThat(help.get("translation").asText()).as("해설 한글 해석").isNotBlank();
      assertThat(help.get("vocab").size()).as("해설 어휘").isPositive();
      assertThat(help.get("solution").asText()).as("해설 풀이").isNotBlank();
      assertThat(help.get("strategy").asText()).as("해설 전략").isNotBlank();
    });

    // when — 정답 키로 답한다. 주관식 '21/2|10.5'는 42/4로 입력해 동치 채점을 확인한다.
    List<Map<String, Object>> answers = new ArrayList<>();
    jdbc.query("SELECT q.question_id, q.answer_key FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
            + " WHERE e.exam_code = ? ORDER BY q.order_no",
        (java.sql.ResultSet rs) -> {
          String key = rs.getString("answer_key");
          answers.add(Map.of("questionId", rs.getLong("question_id"),
              "answerText", "21/2|10.5".equals(key) ? "42/4" : key.split("\\|")[0]));
        }, SAT_EXAM);
    MvcResult submit = mockMvc.perform(post(API + "/attempts/" + attemptId + "/submit").cookie(auth(OWNER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("answers", answers))))
        .andReturn();

    // then
    assertThat(submit.getResponse().getStatus()).isEqualTo(200);
    JsonNode card = json(submit);
    assertThat(card.get("kind").asText()).isEqualTo("SAT");
    assertThat(card.get("totalScaled").asInt()).isEqualTo(1600);
    assertThat(card.get("overallBand").isNull()).isTrue();
    assertThat(card.get("bandDisclaimer").asText()).isEqualTo(SAT_DISCLAIMER);
    List<String> sections = new ArrayList<>();
    card.get("sections").forEach(s -> {
      sections.add(s.get("section").asText());
      assertThat(s.get("scaled").asInt()).isEqualTo(800);
      assertThat(s.get("band").isNull()).isTrue();
    });
    assertThat(sections).containsExactly("RW", "MATH");

    MvcResult review = mockMvc.perform(get(API + "/attempts/" + attemptId + "/review").cookie(auth(OWNER_ID))).andReturn();
    JsonNode rv = json(review);
    assertThat(rv.get("totalScaled").asInt()).isEqualTo(1600);
    rv.get("items").forEach(it -> assertThat(it.get("isCorrect").asBoolean()).as(it.get("prompt").asText()).isTrue());

    JsonNode h = json(mockMvc.perform(get(API + "/attempts").cookie(auth(OWNER_ID))).andReturn()).get(0);
    assertThat(h.get("kind").asText()).isEqualTo("SAT");
    assertThat(h.get("totalScaled").asInt()).isEqualTo(1600);
  }

  @Test
  @DisplayName("SAT 미니 세트: 답 없이 제출하면 영역 200·합계 400, 오답 18건이 오답 목록에 쌓인다")
  void satFlow_blankSubmit() throws Exception {
    MvcResult start = mockMvc.perform(post(API + "/attempts").cookie(auth(OTHER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("examCode", SAT_EXAM))))
        .andReturn();
    long attemptId = json(start).get("attemptId").asLong();

    JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + attemptId + "/submit").cookie(auth(OTHER_ID))
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn());

    assertThat(card.get("totalScaled").asInt()).isEqualTo(400);
    assertThat(json(mockMvc.perform(get(API + "/learning").cookie(auth(OTHER_ID))).andReturn())
        .get("wrongCount").asLong()).isEqualTo(18);
  }

  @Test
  @DisplayName("given SAT 오답, when 오답만 다시 풀어 모두 맞혀도 추정 점수(800·1600)는 비고 정답 수만 나오며, 내 점수 목록에도 점수 없이 정답 수가 있다")
  void satRetry_noEstimate() throws Exception {
    // given — 미니 세트를 답 없이 제출해 18문항이 모두 오답
    JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", SAT_EXAM)))).andReturn());
    mockMvc.perform(post(API + "/attempts/" + view.get("attemptId").asLong() + "/submit").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 3; i++) ids.add(view.get("questions").get(i).get("questionId").asLong());
    // when — 틀린 3문항만 다시 풀어 모두 맞힌다
    JsonNode retry = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", SAT_EXAM, "questionIds", ids)))).andReturn());
    long retryId = retry.get("attemptId").asLong();
    List<Map<String, Object>> answers = new ArrayList<>();
    for (Long id : ids) {
      answers.add(Map.of("questionId", id, "answerText",
          jdbc.queryForObject("SELECT answer_key FROM tp_questions WHERE question_id = ?", String.class, id).split("\\|")[0]));
    }
    JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + retryId + "/submit")
        .cookie(auth(OWNER_ID)).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("answers", answers)))).andReturn());
    // then — 3문항으로 800·1600점을 내지 않는다
    assertThat(retry.get("questions")).hasSize(3);
    assertThat(card.get("totalScaled").isNull()).isTrue();
    assertThat(card.get("totalMax").isNull()).isTrue();
    int correct = 0;
    for (JsonNode s : card.get("sections")) {
      assertThat(s.get("scaled").isNull()).isTrue();
      assertThat(s.get("scaleMax").isNull()).isTrue();
      correct += s.get("correct").asInt();
    }
    assertThat(correct).isEqualTo(3);
    assertThat(json(mockMvc.perform(get(API + "/attempts/" + retryId + "/review").cookie(auth(OWNER_ID))).andReturn())
        .get("totalScaled").isNull()).isTrue();
    for (JsonNode h : json(mockMvc.perform(get(API + "/attempts").cookie(auth(OWNER_ID))).andReturn())) {
      if (h.get("attemptId").asLong() == retryId) {
        assertThat(h.get("totalScaled").isNull()).isTrue();
        assertThat(h.get("correctCount").asInt()).isEqualTo(3);
        assertThat(h.get("autoCount").asInt()).isEqualTo(3);
      } else {
        assertThat(h.get("totalScaled").asInt()).isEqualTo(400); // 전체 응시는 그대로 점수를 낸다
      }
    }
  }

  @Test
  @DisplayName("Practice Test Player에서 옮긴 SAT 세트 2개: 각 21문항, 전원 정답이면 1600, 모든 문항에 해설란 자료")
  void ptpSatSets_allCorrect() throws Exception {
    for (String code : new String[] {"TP-SAT-MINI-02", "TP-SAT-KO-01"}) {
      // given
      JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("examCode", code)))).andReturn());
      assertThat(view.get("questions")).as(code).hasSize(21);
      view.get("questions").forEach(qn -> {
        JsonNode help;
        try { help = objectMapper.readTree(qn.get("koHelpJson").asText()); } catch (Exception e) { throw new AssertionError(e); }
        assertThat(help.get("solution").asText()).as(code + " 풀이").isNotBlank();
        assertThat(help.get("strategy").asText()).as(code + " 전략").isNotBlank();
        assertThat(help.get("vocab").size()).as(code + " 어휘").isPositive();
      });
      // when — 정답 키(주관식은 첫 허용 답)로 제출
      List<Map<String, Object>> answers = new ArrayList<>();
      jdbc.query("SELECT q.question_id, q.answer_key FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
              + " WHERE e.exam_code = ? ORDER BY q.order_no",
          (java.sql.ResultSet rs) -> {
            answers.add(Map.of("questionId", rs.getLong("question_id"), "answerText", rs.getString("answer_key").split("\\|")[0]));
          }, code);
      JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + view.get("attemptId").asLong() + "/submit")
          .cookie(auth(OWNER_ID)).contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("answers", answers)))).andReturn());
      // then
      assertThat(card.get("totalScaled").asInt()).as(code).isEqualTo(1600);
    }
  }

  @Test
  @DisplayName("given SAT 연습 세트 01(V1129), when 정답 키로 제출하면 49문항(읽기·쓰기 27 + 수학 22)·1600점이고 모든 문항에 해설란 자료가 있다")
  void satPractice01_allCorrect() throws Exception {
    // given
    String code = "TP-SAT-PRAC-01";
    JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", code)))).andReturn());
    List<String> sections = new ArrayList<>();
    view.get("questions").forEach(qn -> {
      sections.add(qn.get("section").asText());
      JsonNode help;
      try { help = objectMapper.readTree(qn.get("koHelpJson").asText()); } catch (Exception e) { throw new AssertionError(e); }
      assertThat(help.get("solution").asText()).as("풀이").isNotBlank();
      assertThat(help.get("strategy").asText()).as("전략").isNotBlank();
    });
    // when — 정답 키(주관식은 첫 허용 답)로 제출
    List<Map<String, Object>> answers = new ArrayList<>();
    jdbc.query("SELECT q.question_id, q.answer_key FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
            + " WHERE e.exam_code = ? ORDER BY q.order_no",
        (java.sql.ResultSet rs) -> {
          answers.add(Map.of("questionId", rs.getLong("question_id"), "answerText", rs.getString("answer_key").split("\\|")[0]));
        }, code);
    JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + view.get("attemptId").asLong() + "/submit")
        .cookie(auth(OWNER_ID)).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("answers", answers)))).andReturn());
    // then
    assertThat(sections).hasSize(49);
    assertThat(sections.stream().filter("RW"::equals).count()).isEqualTo(27);
    assertThat(sections.stream().filter("MATH"::equals).count()).isEqualTo(22);
    assertThat(card.get("totalScaled").asInt()).isEqualTo(1600);
  }

  @Test
  @DisplayName("given iBT 연습 세트 09(V1130), when 문항을 조회하면 120문항이고 객관식 정답 글자는 선택지 범위 안, 문장 만들기 정답은 칩을 모두 한 번씩 쓴다")
  void practiceSet09_answerKeysMatchChoices() throws Exception {
    // given
    String code = "TP-PRACTICE-2026-SET09";
    // when
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT q.order_no, q.task_type, q.choices_json, q.answer_key, q.auto_score FROM tp_questions q"
            + " JOIN tp_exams e ON e.exam_id = q.exam_id WHERE e.exam_code = ? ORDER BY q.order_no", code);
    // then
    assertThat(rows).hasSize(120);
    for (Map<String, Object> row : rows) {
      String type = (String) row.get("task_type");
      Object no = row.get("order_no");
      if (row.get("choices_json") == null) continue;
      List<String> choices = new ArrayList<>();
      objectMapper.readTree((String) row.get("choices_json")).forEach(c -> choices.add(c.asText()));
      String key = (String) row.get("answer_key");
      if ("BUILD_SENTENCE".equals(type)) {
        List<String> words = new ArrayList<>(List.of(key.trim().split("\\s+")));
        java.util.Collections.sort(words);
        java.util.Collections.sort(choices);
        assertThat(words).as("문장 만들기 " + no).isEqualTo(choices);
      } else {
        assertThat(key).as("정답 글자 " + no).matches("[A-D]");
        assertThat(key.charAt(0) - 'A').as("정답 위치 " + no).isLessThan(choices.size());
      }
    }
  }

  @Test
  @DisplayName("given iBT 연습 세트 02~08(V1134 보정 뒤), when 문항을 조회하면 읽기·듣기 발문에 실제 질문이 있고 문장 만들기 정답은 칩을 모두 한 번씩 쓴다")
  void practiceSets02to08_promptsCarryQuestions() throws Exception {
    // given
    List<String> questionTypes =
        List.of("READ_DAILY_LIFE", "LISTEN_CONVERSATION", "LISTEN_ANNOUNCEMENT", "LISTEN_TALK");
    for (int set = 2; set <= 8; set++) {
      String code = "TP-PRACTICE-2026-SET0" + set;
      // when
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT q.order_no, q.task_type, q.prompt, q.choices_json, q.answer_key FROM tp_questions q"
              + " JOIN tp_exams e ON e.exam_id = q.exam_id WHERE e.exam_code = ? ORDER BY q.order_no", code);
      // then
      assertThat(rows).as(code).hasSize(120);
      for (Map<String, Object> row : rows) {
        String type = (String) row.get("task_type");
        String where = code + " " + row.get("order_no");
        String prompt = ((String) row.get("prompt")).trim();
        if (questionTypes.contains(type)) {
          assertThat(prompt).as("발문에 질문 " + where).contains("?");
          assertThat(prompt).as("일반 지시문으로 끝나지 않음 " + where).doesNotEndWith("answer the question.");
        } else if ("BUILD_SENTENCE".equals(type)) {
          List<String> chips = new ArrayList<>();
          objectMapper.readTree((String) row.get("choices_json")).forEach(c -> chips.add(c.asText().toLowerCase()));
          List<String> words = new ArrayList<>(List.of(((String) row.get("answer_key")).trim().toLowerCase().split("\\s+")));
          java.util.Collections.sort(chips);
          java.util.Collections.sort(words);
          assertThat(words).as("문장 만들기 " + where).isEqualTo(chips);
        }
      }
    }
  }

  @Test
  @DisplayName("given iBT 연습 세트 01~08·어휘 퀴즈(V1134 점검 뒤), when 자동 채점 문항을 조회하면 객관식 정답은 선택지 범위 안, 문장 만들기 칩은 정답 낱말과 같고, 낱말 완성은 빈칸에 정답을 넣으면 해설의 낱말이 된다")
  void practiceSets01to08_autoScoredAnswersAreConsistent() throws Exception {
    // given
    List<String> codes = new ArrayList<>(
        List.of("TP-PRACTICE-2026-MINI", "TP-VOCAB-EASY", "TP-VOCAB-MEDIUM", "TP-VOCAB-HARD"));
    for (int set = 2; set <= 8; set++) codes.add("TP-PRACTICE-2026-SET0" + set);
    List<String> needsBody = List.of("READ_DAILY_LIFE", "READ_PASSAGE", "LISTEN_RESPONSE",
        "LISTEN_CONVERSATION", "LISTEN_ANNOUNCEMENT", "LISTEN_TALK");
    java.util.regex.Pattern stem = java.util.regex.Pattern.compile(
        "Complete the missing letters of the word: ([a-z]*?)((?:_ ?)+?) ?([a-z]*)");
    for (String code : codes) {
      // when
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT q.order_no, q.task_type, q.prompt, q.body_text, q.choices_json, q.answer_key, q.explanation"
              + " FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
              + " WHERE e.exam_code = ? AND q.auto_score = 1 ORDER BY q.order_no", code);
      // then
      assertThat(rows).as(code).isNotEmpty();
      for (Map<String, Object> row : rows) {
        String type = (String) row.get("task_type");
        String where = code + " " + row.get("order_no");
        String prompt = ((String) row.get("prompt")).trim();
        String key = ((String) row.get("answer_key")).trim();
        List<String> choices = new ArrayList<>();
        objectMapper.readTree((String) row.get("choices_json")).forEach(c -> choices.add(c.asText()));
        assertThat(prompt).as("일반 지시문으로 끝나지 않음 " + where).doesNotEndWith("answer the question.");
        if ("BUILD_SENTENCE".equals(type)) {
          List<String> chips = new ArrayList<>();
          choices.forEach(c -> chips.add(c.toLowerCase()));
          List<String> words = new ArrayList<>(List.of(key.toLowerCase().split("\\s+")));
          java.util.Collections.sort(chips);
          java.util.Collections.sort(words);
          assertThat(words).as("문장 만들기 " + where).isEqualTo(chips);
          continue;
        }
        assertThat(key).as("정답 글자 " + where).matches("[A-D]");
        assertThat(key.charAt(0) - 'A').as("정답 위치 " + where).isLessThan(choices.size());
        assertThat(choices).as("선택지 중복 없음 " + where).doesNotHaveDuplicates();
        if (needsBody.contains(type)) {
          assertThat((String) row.get("body_text")).as("지문·대본 " + where).isNotBlank();
        }
        if ("COMPLETE_THE_WORDS".equals(type)) {
          java.util.regex.Matcher m = stem.matcher(prompt);
          assertThat(m.matches()).as("낱말 완성 발문 꼴 " + where).isTrue();
          String word = ((String) row.get("explanation")).split(" ")[0];
          String made = m.group(1) + choices.get(key.charAt(0) - 'A') + m.group(3);
          assertThat(made).as("빈칸에 정답을 넣은 낱말 " + where).isEqualTo(word);
        }
      }
    }
  }

  @Test
  @DisplayName("given 내신 1회(40문항·134점), when 정답 37개·오답 3개로 제출하면 맞힌 배점 합과 만점·문항별 배점·해설을 돌려준다")
  void naesinRound_pointsScoring() throws Exception {
    // given
    String code = "TP-NAESIN-APM2-2602-R01";
    MvcResult list = mockMvc.perform(get(API + "/exams").param("kind", "NAESIN").cookie(auth(OWNER_ID))).andReturn();
    assertThat(json(list)).hasSize(35); // 압구정중2 16회 + 신구중2 Lesson 7·8 10회(V1120) + 종합 Final 2회(V1121·V1136) + 압구정중1 7회(V1127)
    MvcResult start = mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("examCode", code))))
        .andReturn();
    JsonNode view = json(start);
    long attemptId = view.get("attemptId").asLong();
    assertThat(view.get("kind").asText()).isEqualTo("NAESIN");
    assertThat(view.get("questions")).hasSize(40);
    view.get("questions").forEach(q -> {
      assertThat(q.get("prompt").asText()).startsWith("[[img:/naesin/apgujeong-m2-2026-2/r01/q");
      assertThat(q.get("koHelpJson").isNull()).as("풀이 중 해설 없음").isTrue();
    });

    // when — 앞의 3문항은 일부러 틀리게 답한다.
    List<Map<String, Object>> answers = new ArrayList<>();
    int[] lost = {0};
    jdbc.query("SELECT q.question_id, q.answer_key, q.points FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
            + " WHERE e.exam_code = ? ORDER BY q.order_no",
        (java.sql.ResultSet rs) -> {
          String key = rs.getString("answer_key");
          boolean miss = answers.size() < 3;
          if (miss) lost[0] += rs.getInt("points");
          answers.add(Map.of("questionId", rs.getLong("question_id"), "answerText", miss ? ("A".equals(key) ? "B" : "A") : key));
        }, code);
    MvcResult submit = mockMvc.perform(post(API + "/attempts/" + attemptId + "/submit").cookie(auth(OWNER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("answers", answers))))
        .andReturn();

    // then
    JsonNode card = json(submit);
    assertThat(card.get("totalMax").asInt()).isEqualTo(134);
    assertThat(card.get("totalScaled").asInt()).isEqualTo(134 - lost[0]);
    assertThat(card.get("bandDisclaimer").asText()).isEqualTo("F2R 예상문제 연습 점수입니다. 실제 학교 시험 점수가 아닙니다.");
    assertThat(card.get("sections").get(0).get("correct").asInt()).isEqualTo(37);
    assertThat(card.get("sections").get(0).get("band").isNull()).isTrue();
    JsonNode review = json(mockMvc.perform(get(API + "/attempts/" + attemptId + "/review").cookie(auth(OWNER_ID))).andReturn());
    assertThat(review.get("totalScaled").asInt()).isEqualTo(134 - lost[0]);
    assertThat(review.get("items")).hasSize(40);
    review.get("items").forEach(it -> {
      assertThat(it.get("points").asInt()).isPositive();
      assertThat(it.get("explanation").asText()).startsWith("정답 ");
    });
    JsonNode hist = json(mockMvc.perform(get(API + "/attempts").cookie(auth(OWNER_ID))).andReturn());
    assertThat(hist.get(0).get("totalMax").asInt()).isEqualTo(134);
  }

  @Test
  @DisplayName("given 압구정중2 내신 16회분, when 회차별 배점을 더하면 문제지 표지 총점과 같고 종합 11~13회만 70문항이다")
  void naesinRounds_pointTotals() {
    // given / when
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT e.exam_code, COUNT(*) n, SUM(q.points) pts FROM tp_exams e JOIN tp_questions q ON q.exam_id = e.exam_id"
            + " WHERE e.kind = 'NAESIN' AND e.exam_code LIKE 'TP-NAESIN-APM2-%' GROUP BY e.exam_code ORDER BY e.exam_code");
    // then
    int[] expected = {134, 134, 135, 136, 135, 134, 136, 134, 135, 134, 237, 236, 237, 137, 138, 135};
    assertThat(rows).hasSize(16);
    for (int i = 0; i < 16; i++) {
      assertThat(((Number) rows.get(i).get("pts")).intValue()).as("R%02d 배점 합", i + 1).isEqualTo(expected[i]);
      assertThat(((Number) rows.get(i).get("n")).intValue()).as("R%02d 문항 수", i + 1).isEqualTo(i >= 10 && i <= 12 ? 70 : 40);
    }
  }

  @Test
  @DisplayName("given 신구중2 12회분(V1120 10회 + V1121·V1136 종합 Final 2회), when 회차별로 세면 모두 40문항·100점이고 모두 고르기 문항은 정답이 둘 이상이다")
  void singuRounds_countsPointsAndMultiKeys() {
    // given / when
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT e.exam_code, COUNT(*) n, SUM(q.points) pts, SUM(q.task_type = 'NAESIN_MC5_MULTI') multi,"
            + " SUM(q.task_type = 'NAESIN_MC5_MULTI' AND q.answer_key NOT LIKE '%,%') bad"
            + " FROM tp_exams e JOIN tp_questions q ON q.exam_id = e.exam_id"
            + " WHERE e.exam_code LIKE 'TP-NAESIN-SGM2-2602-R%' GROUP BY e.exam_code ORDER BY e.exam_code");
    // then
    assertThat(rows).hasSize(12);
    for (Map<String, Object> r : rows) {
      assertThat(((Number) r.get("n")).intValue()).as(r.get("exam_code") + " 문항 수").isEqualTo(40);
      assertThat(((Number) r.get("pts")).intValue()).as(r.get("exam_code") + " 배점 합").isEqualTo(100);
      assertThat(((Number) r.get("multi")).intValue()).as(r.get("exam_code") + " 모두 고르기").isPositive();
      assertThat(((Number) r.get("bad")).intValue()).as(r.get("exam_code") + " 모두 고르기 정답 형식").isZero();
    }
  }

  @Test
  @DisplayName("given 신구중2 12회(V1136), when 문항을 순서대로 읽으면 then 40문항·100점이고 모두 고르기는 7·10·21·37번이며 그림은 r12 폴더를 가리킨다")
  void singuRound12_countsPointsMultiAndImages() {
    // given
    String sql = "SELECT q.order_no, q.task_type, q.answer_key, q.points, q.prompt, q.body_text, q.explanation"
        + " FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
        + " WHERE e.exam_code = 'TP-NAESIN-SGM2-2602-R12' ORDER BY q.order_no";
    // when
    List<Map<String, Object>> rows = jdbc.queryForList(sql);
    // then
    assertThat(rows).hasSize(40);
    assertThat(rows.stream().mapToInt(r -> ((Number) r.get("points")).intValue()).sum()).isEqualTo(100);
    assertThat(rows.stream().filter(r -> "NAESIN_MC5_MULTI".equals(r.get("task_type")))
        .map(r -> ((Number) r.get("order_no")).intValue())).containsExactly(7, 10, 21, 37);
    for (Map<String, Object> r : rows) {
      int no = ((Number) r.get("order_no")).intValue();
      boolean multi = "NAESIN_MC5_MULTI".equals(r.get("task_type"));
      assertThat((String) r.get("answer_key")).as(no + "번 정답").matches(multi ? "[A-E],[A-E]" : "[A-E]");
      assertThat((String) r.get("prompt")).as(no + "번 문항 그림")
          .isEqualTo(String.format("[[img:/naesin/singu-m2-2026-2/r12/q%02d.webp]]", no));
      assertThat(getClass().getResource(String.format("/static/naesin/singu-m2-2026-2/r12/q%02d.webp", no)))
          .as(no + "번 그림 파일").isNotNull();
      String body = (String) r.get("body_text");
      if (body != null) {
        assertThat(body).as(no + "번 지문 그림").matches("\\[\\[img:/naesin/singu-m2-2026-2/r12/p\\d\\d-\\d\\d\\.webp\\]\\]");
        assertThat(getClass().getResource("/static" + body.substring(6, body.length() - 2))).as(no + "번 지문 파일").isNotNull();
      }
      assertThat((String) r.get("explanation")).as(no + "번 해설").startsWith("정답 ").contains("\n근거: ");
    }
    assertThat(rows.get(0).get("answer_key")).isEqualTo("C");
    assertThat(rows.get(36).get("answer_key")).isEqualTo("B,D");
  }

  @Test
  @DisplayName("given 압구정중1 2회 12번(V1128), when 해설을 읽으면 then 발문이 다른 회와 같은 「밑줄 친 곳이 ⓐ와 같은 쓰임으로 사용된 것은?」이고 정답·배점은 그대로다")
  void apgujeongM1Round2Q12_stemMatchesOtherRounds() {
    // given
    String sql = "SELECT q.explanation, q.answer_key, q.points FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
        + " WHERE e.exam_code = 'TP-NAESIN-APM1-2602-R02' AND q.order_no = 12";
    // when
    Map<String, Object> row = jdbc.queryForMap(sql);
    // then
    assertThat((String) row.get("explanation"))
        .startsWith("정답 ⑤ · 밑줄 친 곳이 ⓐ와 같은 쓰임으로 사용된 것은? (3점)")
        .doesNotContain("밑줄 친 부분의 쓰임이");
    assertThat(row.get("answer_key")).isEqualTo("E");
    assertThat(((Number) row.get("points")).intValue()).isEqualTo(3);
  }

  @Test
  @DisplayName("given 신구중2 1회, when 모두 고르기 문항을 순서를 바꿔 다 맞히고 한 문항은 일부만 고르면 그 문항만 틀리고 배점이 빠진다")
  void singuRound1_multiChoiceScoring() throws Exception {
    // given
    String code = "TP-NAESIN-SGM2-2602-R01";
    JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("examCode", code)))).andReturn());
    long attemptId = view.get("attemptId").asLong();
    assertThat(view.get("questions")).hasSize(40);
    assertThat(view.get("questions").get(0).get("prompt").asText()).startsWith("[[img:/naesin/singu-m2-2026-2/r01/q");

    // when — 모두 고르기 문항은 정답 기호를 거꾸로 적고, 첫 모두 고르기 문항만 하나를 빼고 낸다.
    List<Map<String, Object>> answers = new ArrayList<>();
    int[] lost = {0};
    jdbc.query("SELECT q.question_id, q.task_type, q.answer_key, q.points FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
            + " WHERE e.exam_code = ? ORDER BY q.order_no",
        (java.sql.ResultSet rs) -> {
          String key = rs.getString("answer_key");
          String given = key;
          if ("NAESIN_MC5_MULTI".equals(rs.getString("task_type"))) {
            List<String> parts = new ArrayList<>(List.of(key.split(",")));
            java.util.Collections.reverse(parts);
            if (lost[0] == 0) { parts.remove(0); lost[0] = rs.getInt("points"); }
            given = String.join(",", parts);
          }
          answers.add(Map.of("questionId", rs.getLong("question_id"), "answerText", given));
        }, code);
    JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + attemptId + "/submit").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("answers", answers)))).andReturn());

    // then
    assertThat(lost[0]).isPositive();
    assertThat(card.get("totalMax").asInt()).isEqualTo(100);
    assertThat(card.get("totalScaled").asInt()).isEqualTo(100 - lost[0]);
    assertThat(card.get("sections").get(0).get("correct").asInt()).isEqualTo(39);
  }

  @Test
  @DisplayName("given 발음부호 문항(V1117), when 해설을 읽으면 강세가 아니라 발음부호 오류를 설명하고 정답 번호는 그대로다")
  void naesinIpaQuestions_explainSymbolErrors() {
    // given / when
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT e.exam_code, q.order_no, q.answer_key, q.explanation FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
            + " WHERE e.kind = 'NAESIN' AND q.explanation LIKE '정답 _ · 발음부호%' ORDER BY e.exam_code, q.order_no");
    // then
    assertThat(rows).hasSize(32);
    assertThat(rows).noneMatch(r -> r.get("explanation").toString().contains("주강세"));
    Map<String, Object> r1 = rows.get(0);
    assertThat(r1.get("answer_key")).isEqualTo("B");
    assertThat(r1.get("explanation").toString()).contains("바른 발음부호는 [prɪˈzɜːrv]", "②는 [s]로 적어 틀리다");
  }

  @Test
  @DisplayName("given TOEIC 세트 3개, when 정답 키로 제출하면 영역 495(1회분 합계 990·독해 세트 495)와 TOEIC 안내 문구, 밴드는 없다")
  void toeicSets_allCorrect() throws Exception {
    // given — kind=TOEIC 목록에 이 세트 3개가 있고(세트가 더 늘어도 된다) 문항 수는 1회분 200(듣기 100 + 읽기 100, V1135에서 Part 1 6문항 추가), 독해 세트 100
    List<String> codes = new ArrayList<>();
    json(mockMvc.perform(get(API + "/exams").param("kind", "TOEIC").cookie(auth(OWNER_ID))).andReturn())
        .forEach(e -> codes.add(e.get("examCode").asText()));
    Map<String, int[]> expected = Map.of( // {문항 수, 합계 만점}
        "TP-TOEIC-FULL-01", new int[] {200, 990}, "TP-TOEIC-RC-01", new int[] {100, 495}, "TP-TOEIC-RC-02", new int[] {100, 495});
    assertThat(codes).containsAll(expected.keySet());
    for (String code : expected.keySet()) {
      JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("examCode", code)))).andReturn());
      assertThat(view.get("kind").asText()).isEqualTo("TOEIC");
      assertThat(view.get("questions")).as(code).hasSize(expected.get(code)[0]);
      assertThat(view.get("bandDisclaimer").asText()).isEqualTo(TestPrepService.TOEIC_DISCLAIMER);
      // when — 정답 키로 제출
      List<Map<String, Object>> answers = new ArrayList<>();
      jdbc.query("SELECT q.question_id, q.answer_key FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
              + " WHERE e.exam_code = ? ORDER BY q.order_no",
          (java.sql.ResultSet rs) -> {
            answers.add(Map.of("questionId", rs.getLong("question_id"), "answerText", rs.getString("answer_key")));
          }, code);
      JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + view.get("attemptId").asLong() + "/submit")
          .cookie(auth(OWNER_ID)).contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(Map.of("answers", answers)))).andReturn());
      // then
      assertThat(card.get("totalScaled").asInt()).as(code).isEqualTo(expected.get(code)[1]);
      assertThat(card.get("totalMax").asInt()).as(code).isEqualTo(expected.get(code)[1]);
      assertThat(card.get("overallBand").isNull()).isTrue();
      card.get("sections").forEach(sec -> {
        assertThat(sec.get("scaled").asInt()).isEqualTo(495);
        assertThat(sec.get("band").isNull()).isTrue();
      });
      // 점수표의 영역 순서는 실제 시험처럼 듣기 → 읽기
      assertThat(card.get("sections").get(0).get("section").asText()).as(code)
          .isEqualTo(expected.get(code)[0] == 200 ? "LISTENING" : "READING");
    }
  }

  @Test
  @DisplayName("given TOEIC 독해 세트, when Part 5·6만 골라 풀면, then 46문항·35분으로 시작하고 모두 맞혀도 추정 점수 없이 정답 수만 나온다")
  void toeicPartsOnly_noEstimate() throws Exception {
    // given & when — 100문항 75분 세트에서 Part 5·6만
    JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", "TP-TOEIC-RC-01",
            "taskTypes", List.of("TOEIC_P5", "TOEIC_P6"))))).andReturn());
    List<Map<String, Object>> answers = new ArrayList<>();
    for (JsonNode q : view.get("questions")) {
      long id = q.get("questionId").asLong();
      answers.add(Map.of("questionId", id, "answerText",
          jdbc.queryForObject("SELECT answer_key FROM tp_questions WHERE question_id = ?", String.class, id)));
    }
    JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + view.get("attemptId").asLong() + "/submit")
        .cookie(auth(OWNER_ID)).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("answers", answers)))).andReturn());
    // then
    assertThat(view.get("questions")).hasSize(46);
    view.get("questions").forEach(q -> assertThat(q.get("taskType").asText()).isIn("TOEIC_P5", "TOEIC_P6"));
    assertThat(view.get("remainingSec").asLong()).isBetween(2090L, 2100L);
    assertThat(card.get("totalScaled").isNull()).isTrue();
    assertThat(card.get("sections").get(0).get("correct").asInt()).isEqualTo(46);
    assertThat(card.get("sections").get(0).get("scaled").isNull()).isTrue();
  }

  @Test
  @DisplayName("given TOEIC 1회분 세트(120분 194문항), when Part 7 끝부분 176~200번만 풀면, then 이중·삼중 지문 25문항이 나오고 시간은 문항당 45초(19분)다")
  void toeicPart7LastPart() throws Exception {
    // when
    JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", "TP-TOEIC-FULL-01",
            "taskTypes", List.of("TOEIC_P7"), "fromNo", 176, "toNo", 200)))).andReturn());
    // then
    assertThat(view.get("questions")).hasSize(25);
    view.get("questions").forEach(q -> assertThat(q.get("orderNo").asInt()).isBetween(176, 200));
    assertThat(view.get("questions").get(0).get("bodyText").asText()).startsWith("Questions 176-180");
    assertThat(view.get("remainingSec").asLong()).isBetween(1130L, 1140L);
  }

  @Test
  @DisplayName("given TOEIC 독해 세트, when Part 5의 품사 유형만 고르면 그 유형 5문항만 나오고, TOEIC이 아닌 세트에 파트를 고르면 400이다")
  void toeicFocusTag_andOnlyForToeic() throws Exception {
    // given & when
    JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", "TP-TOEIC-RC-01",
            "taskTypes", List.of("TOEIC_P5"), "focusTag", "문법-품사")))).andReturn());
    MvcResult sat = mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", SAT_EXAM, "taskTypes", List.of("TOEIC_P5"))))).andReturn();
    // then
    assertThat(view.get("questions")).hasSize(5);
    view.get("questions").forEach(q -> assertThat(q.get("taskType").asText()).isEqualTo("TOEIC_P5"));
    assertThat(sat.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("given TOEIC 1회분(V1131 + V1135), when 문항을 읽으면 1~200번 200문항이고 Part 1 6문항은 TOEIC_P1·그림 줄, 그래픽 묶음 세 문항은 같은 body_text다")
  void toeicFull01_part1AndGraphicSets() {
    // given
    String code = "TP-TOEIC-FULL-01";
    // when
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT q.order_no, q.section, q.task_type, q.audio_mode, q.prompt, q.body_text, q.choices_json FROM tp_questions q"
            + " JOIN tp_exams e ON e.exam_id = q.exam_id WHERE e.exam_code = ? ORDER BY q.order_no", code);
    Map<Integer, Map<String, Object>> byNo = new java.util.HashMap<>();
    rows.forEach(r -> byNo.put(((Number) r.get("order_no")).intValue(), r));
    // then — 번호는 실제 시험처럼 1~200 연속, 듣기 100 + 읽기 100
    assertThat(rows).hasSize(200);
    assertThat(byNo.keySet()).containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.rangeClosed(1, 200).boxed().toList());
    assertThat(rows.stream().filter(r -> "LISTENING".equals(r.get("section"))).count()).isEqualTo(100);
    // Part 1: 1~6번, TOEIC_P1, 글 없는 선택지 4개, 보기 네 줄 + [[show]] + 그림 한 줄
    for (int no = 1; no <= 6; no++) {
      Map<String, Object> q = byNo.get(no);
      assertThat(q.get("task_type")).as("No. " + no).isEqualTo("TOEIC_P1");
      assertThat(q.get("audio_mode")).isEqualTo("TTS");
      assertThat(q.get("choices_json")).isEqualTo("[\"\", \"\", \"\", \"\"]");
      String[] lines = ((String) q.get("body_text")).split("\n");
      assertThat(lines).hasSize(6);
      assertThat(lines[0]).startsWith("A. ");
      assertThat(lines[3]).startsWith("D. ");
      assertThat(lines[4]).isEqualTo("[[show]]");
    }
    assertThat(rows.stream().filter(r -> "TOEIC_P1".equals(r.get("task_type"))).count()).isEqualTo(6);
    // 그림 줄은 모두 /testprep/img/ 아래의 실제 파일을 가리킨다
    List<String> images = new ArrayList<>();
    rows.forEach(r -> {
      for (String line : String.valueOf(r.get("body_text")).split("\n")) {
        if (line.contains("[[img:")) images.add(line);
      }
    });
    assertThat(images).hasSize(6 + 3); // Part 1 6장 + 95~97번 평면도(세 문항에 같은 줄)
    images.forEach(line -> {
      assertThat(line).matches("\\[\\[img:/testprep/img/[A-Za-z0-9_\\-./]+\\.svg\\]\\]");
      String path = line.substring("[[img:".length(), line.length() - 2);
      assertThat(getClass().getResource("/static" + path)).as(path).isNotNull();
    });
    // 그래픽 묶음 5개: 세 문항의 body_text가 글자까지 같고 [[show]] 뒤에 시각자료, 그래픽 문항은 묶음마다 하나
    for (int first : new int[] {62, 65, 68, 95, 98}) {
      String body = (String) byNo.get(first).get("body_text");
      assertThat(body).as("No. " + first).contains("\n[[show]]\n");
      assertThat(byNo.get(first + 1).get("body_text")).isEqualTo(body);
      assertThat(byNo.get(first + 2).get("body_text")).isEqualTo(body);
      long graphic = java.util.stream.IntStream.range(first, first + 3)
          .filter(no -> ((String) byNo.get(no).get("prompt")).startsWith("Look at the graphic. ")).count();
      assertThat(graphic).as("No. " + first).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("given SAT 연습 세트 01(V1129 + V1135), when 그림 문항 4개를 읽으면 그림 줄이 /testprep/img/ 아래의 실제 파일을 가리킨다")
  void satPractice01_figureQuestions() {
    // given
    String code = "TP-SAT-PRAC-01";
    // when
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT q.order_no, q.prompt, q.body_text FROM tp_questions q JOIN tp_exams e ON e.exam_id = q.exam_id"
            + " WHERE e.exam_code = ? AND (q.prompt LIKE '%[[img:%' OR q.body_text LIKE '%[[img:%') ORDER BY q.order_no", code);
    // then
    assertThat(rows.stream().map(r -> ((Number) r.get("order_no")).intValue()).toList()).containsExactly(12, 29, 33, 39);
    rows.forEach(r -> {
      String text = r.get("prompt") + "\n" + r.get("body_text");
      String line = java.util.Arrays.stream(text.split("\n")).filter(l -> l.contains("[[img:")).findFirst().orElseThrow();
      assertThat(line).isEqualTo("[[img:/testprep/img/sat_prac01_q" + r.get("order_no") + ".svg]]");
      assertThat(getClass().getResource("/static/testprep/img/sat_prac01_q" + r.get("order_no") + ".svg")).isNotNull();
    });
  }

  @Test
  @DisplayName("given TOEIC 오답, when 오답만 다시 풀어 모두 맞혀도 추정 점수는 비고 정답 수만 나오며, 오답 목록에는 실제 문항 번호가 있다")
  void toeicRetry_noEstimate() throws Exception {
    // given — 독해 세트를 답 없이 제출해 100문항이 모두 오답
    JsonNode view = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", "TP-TOEIC-RC-01")))).andReturn());
    mockMvc.perform(post(API + "/attempts/" + view.get("attemptId").asLong() + "/submit").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    JsonNode lv = json(mockMvc.perform(get(API + "/learning").cookie(auth(OWNER_ID))).andReturn());
    lv.get("wrongItems").forEach(w -> assertThat(w.get("orderNo").asInt()).isBetween(101, 200));
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 3; i++) ids.add(view.get("questions").get(i).get("questionId").asLong());
    // when — 틀린 3문항만 다시 풀어 모두 맞힌다
    JsonNode retry = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OWNER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", "TP-TOEIC-RC-01", "questionIds", ids)))).andReturn());
    List<Map<String, Object>> answers = new ArrayList<>();
    for (Long id : ids) {
      answers.add(Map.of("questionId", id, "answerText",
          jdbc.queryForObject("SELECT answer_key FROM tp_questions WHERE question_id = ?", String.class, id)));
    }
    JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + retry.get("attemptId").asLong() + "/submit")
        .cookie(auth(OWNER_ID)).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("answers", answers)))).andReturn());
    // then — 3문항으로 495점을 내지 않는다
    assertThat(retry.get("questions")).hasSize(3);
    assertThat(card.get("totalScaled").isNull()).isTrue();
    assertThat(card.get("sections")).hasSize(1);
    assertThat(card.get("sections").get(0).get("scaled").isNull()).isTrue();
    assertThat(card.get("sections").get(0).get("correct").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName("given TOEIC 독해 세트, when 답 없이 제출하면 읽기 5점(최저)이고 문항마다 한국어 해설이 있다")
  void toeicReading_blankSubmit() throws Exception {
    // given
    long attemptId = json(mockMvc.perform(post(API + "/attempts").cookie(auth(OTHER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", "TP-TOEIC-RC-01")))).andReturn()).get("attemptId").asLong();
    // when
    JsonNode card = json(mockMvc.perform(post(API + "/attempts/" + attemptId + "/submit").cookie(auth(OTHER_ID))
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn());
    JsonNode rv = json(mockMvc.perform(get(API + "/attempts/" + attemptId + "/review").cookie(auth(OTHER_ID))).andReturn());
    // then
    assertThat(card.get("totalScaled").asInt()).isEqualTo(5);
    assertThat(card.get("sections")).hasSize(1);
    assertThat(card.get("sections").get(0).get("section").asText()).isEqualTo("READING");
    assertThat(rv.get("items")).hasSize(100);
    assertThat(rv.get("items").get(0).get("orderNo").asInt()).isEqualTo(101);
    rv.get("items").forEach(it -> {
      assertThat(it.get("taskType").asText()).startsWith("TOEIC_P");
      assertThat(it.get("explanation").asText()).contains("정답");
    });
  }

  private int startStatus(Cookie cookie, String code) throws Exception {
    return mockMvc.perform(post(API + "/attempts").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("examCode", code)))).andReturn().getResponse().getStatus();
  }

  @Test
  @DisplayName("given 내신 회차, when 일반 회원·재원생·관리자가 시작하면 일반 회원은 1회만, 재원생·관리자는 전 회차를 풀 수 있다")
  void naesinAccess_generalOnlyRound1_studentsAndAdminAll() throws Exception {
    // given — 일반 회원(학생부에 없음), 학생부 재원생과 이름이 같은 회원, 관리자
    Cookie general = auth(OWNER_ID);
    String login = "naesin-roster-" + System.nanoTime();
    jdbc.update("INSERT INTO users (login_id, password, name, nickname, role) VALUES (?, 'pw', '재원생내신테스트', '재원생', 'STUDENT')", login);
    long rosterUserId = jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?", Long.class, login);
    jdbc.update("INSERT INTO easy_students (name, school, grade, status) VALUES ('재원생내신테스트', '압구정중', 2, 'ENROLLED')");
    Cookie roster = new Cookie("ACCESS_TOKEN", tokenProvider.issue(rosterUserId, login, "STUDENT"));
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(900909L, "tp_admin", "ADMIN"));

    // when / then
    assertThat(startStatus(general, "TP-NAESIN-APM2-2602-R01")).isEqualTo(200);
    assertThat(startStatus(general, "TP-NAESIN-APM2-2602-R02")).isEqualTo(403);
    assertThat(startStatus(roster, "TP-NAESIN-APM2-2602-R02")).isEqualTo(200);
    assertThat(startStatus(admin, "TP-NAESIN-APM2-2602-R16")).isEqualTo(200);
    assertThat(json(mockMvc.perform(get("/api/naesintest/access").cookie(general)).andReturn()).get("full").asBoolean()).isFalse();
    assertThat(json(mockMvc.perform(get("/api/naesintest/access").cookie(roster)).andReturn()).get("full").asBoolean()).isTrue();
    assertThat(mockMvc.perform(get("/naesin/apgujeong-m2-2026-2/r02/q01.webp").cookie(general)).andReturn().getResponse().getStatus()).isEqualTo(403);
    assertThat(mockMvc.perform(get("/naesin/apgujeong-m2-2026-2/r01/q01.webp").cookie(general)).andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(mockMvc.perform(get("/naesin/apgujeong-m2-2026-2/r02/q01.webp").cookie(roster)).andReturn().getResponse().getStatus()).isEqualTo(200);
  }
}
