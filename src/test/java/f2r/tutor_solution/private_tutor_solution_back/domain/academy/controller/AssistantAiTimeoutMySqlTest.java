package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;


/**
 * AI 응답 지연(서버 한도 초과) 시: 요청은 한도 안에 끝나고, 호출은 취소되며, 기존 작업본은 그대로 보존된다.
 * 한도는 buildit.assistant.ai-timeout-seconds(여기서는 1초). 규칙으로 처리되는 요청은 규칙 해석으로 계속된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "buildit.assistant.enabled=true",
    "buildit.assistant.ai-timeout-seconds=1"
})
@DisplayName("AI 응답 지연 — 서버 한도·취소·작업본 보존 (MySQL 통합)")
class AssistantAiTimeoutMySqlTest {
  private static final String API = "/api/tutor/academy/assistant";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
  @MockitoBean private AiProvider aiProvider;

  private User tutor;
  private Cookie cookie;

  /** 계정을 만들고 그 계정으로 로그인한 상태로 둔다. */
  private void loginAs(String loginId) {
    tutor = userRepository.save(new User(loginId, "pw", "스위치강사", "별명", null, null, Role.TUTOR));
    cookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR"));
    authenticate();
  }

  /** true면 AI 제공자가 한도보다 오래(5초) 걸린다. 인터럽트(취소)되면 즉시 실패로 끝난다. */
  private final AtomicBoolean hang = new AtomicBoolean(false);
  private final AtomicInteger interrupted = new AtomicInteger();

  @BeforeEach
  void stubAi() {
    subjectRepository.findByNameKo("영어").orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    org.mockito.Mockito.when(aiProvider.isReady()).thenReturn(true);
    org.mockito.Mockito.when(aiProvider.name()).thenReturn("scripted");
    org.mockito.Mockito.when(aiProvider.complete(any(), any())).thenAnswer(inv -> {
      if (hang.get()) {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          interrupted.incrementAndGet();
          Thread.currentThread().interrupt();
          return AiProvider.AiCompletion.failed("interrupted");
        }
      }
      return AiProvider.AiCompletion.failed("no script");
    });
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(tutor.getUserId(), tutor.getLoginId(), "TUTOR"),
        null, List.of()));
  }

  private MvcResult turn(Map<String, Object> body) throws Exception {
    MvcResult r = mockMvc.perform(post(API + "/turn").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andReturn();
    authenticate();
    return r;
  }

  private MvcResult postJson(String path, String body) throws Exception {
    MvcResult r = mockMvc.perform(post(API + path).cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    authenticate();
    return r;
  }

  private MvcResult upload() throws Exception {
    MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    MvcResult r = mockMvc.perform(multipart(API + "/media").file(image)
        .param("slot", "principal").param("revision", "1").cookie(cookie)).andReturn();
    authenticate();
    return r;
  }

  /** 기존 방식(대화 기록 없음)으로 학원을 만든다. */
  private void createLegacyAcademy() throws Exception {
    Integer subjectId = subjectRepository.findByNameKo("영어").orElseThrow().getSubjectId();
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "기존방식학원", "intro", "기존 빌더로 만든 학원", "subjectIds", List.of(subjectId),
        "targetGroups", List.of("MIDDLE"), "regions", List.of(Map.of("city", "서울", "district", "강남구")),
        "pageSettings", Map.of("builderDraft", Map.of("designPreset", "EXAM", "colorPrimary", "#112233"))));
    MvcResult r = mockMvc.perform(post("/api/tutor/academy").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    authenticate();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
  }

  private int count(String sql) {
    em.flush();
    return jdbc.queryForObject(sql, Integer.class, tutor.getUserId());
  }

  private int academies() { return count("SELECT COUNT(*) FROM academies WHERE tutor_user_id = ?"); }

  private int messages() {
    return count("SELECT COUNT(*) FROM academy_assistant_message m JOIN academies a ON a.academy_id = m.academy_id WHERE a.tutor_user_id = ?");
  }

  private int workingCopies() {
    return count("SELECT COUNT(*) FROM academy_working_copy w JOIN academies a ON a.academy_id = w.academy_id WHERE a.tutor_user_id = ?");
  }

  private String liveJson() {
    em.flush();
    return jdbc.queryForObject("SELECT CONCAT_WS('|', name, intro, CAST(page_settings AS CHAR)) FROM academies WHERE tutor_user_id = ?", String.class, tutor.getUserId());
  }

  private void assertDisabled(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("ASSISTANT_DISABLED");
  }

  private static final Map<String, Object> CREATE = Map.of(
      "academyName", "새봄영어학원", "sampleKey", "exam", "region", "서울 강남구", "subject", "영어", "target", "중학생");


  private MvcResult fetch(String path) throws Exception {
    MvcResult r = mockMvc.perform(get(path).cookie(cookie)).andReturn();
    authenticate();
    return r;
  }

  private JsonNode body(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  private String pageView() throws Exception {
    return fetch("/builder").getModelAndView().getViewName();
  }

  private int versions() {
    return count("SELECT COUNT(*) FROM academy_page_versions v JOIN academies a ON a.academy_id = v.academy_id WHERE a.tutor_user_id = ?");
  }

  private int kindCount(String kind) {
    em.flush();
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_assistant_message m JOIN academies a ON a.academy_id = m.academy_id WHERE a.tutor_user_id = ? AND m.kind = ?",
        Integer.class, tutor.getUserId(), kind);
  }

  private long revision() throws Exception {
    return body(fetch("/api/tutor/academy/working-copy")).get("revision").asLong();
  }

  private void assertNotAssistant(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(body(r).get("code").asText()).isEqualTo("NOT_ASSISTANT_ACADEMY");
  }



  private String workingCopyJson() throws Exception {
    em.flush();
    return jdbc.queryForObject(
        "SELECT CONCAT_WS('|', w.revision, CAST(w.page_settings_json AS CHAR), w.intro) FROM academy_working_copy w JOIN academies a ON a.academy_id = w.academy_id WHERE a.tutor_user_id = ?",
        String.class, tutor.getUserId());
  }

  private int editMessages() {
    em.flush();
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_assistant_message m JOIN academies a ON a.academy_id = m.academy_id WHERE a.tutor_user_id = ? AND m.kind = 'EDIT'",
        Integer.class, tutor.getUserId());
  }

  private String login() { return "tmo_" + UUID.randomUUID().toString().substring(0, 8); }

  private int allMessages() {
    em.flush();
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_assistant_message m JOIN academies a ON a.academy_id = m.academy_id WHERE a.tutor_user_id = ?",
        Integer.class, tutor.getUserId());
  }

  @Test
  @DisplayName("given AI가 한도보다 오래 걸림, when 수정 턴, then 한도 안에 504 AI_TIMEOUT으로 끝나고 호출이 취소되며 작업본·공개본·대화 기록이 그대로다(성공 표시 아님)")
  void slowAiEndsWith504AndPreservesEverything() throws Exception {
    // given
    loginAs(login());
    assertThat(turn(CREATE).getResponse().getStatus()).isEqualTo(200);
    String wcBefore = workingCopyJson();
    String liveBefore = liveJson();
    long rev = revision();
    int messagesBefore = allMessages();
    hang.set(true);

    // when
    long t0 = System.nanoTime();
    MvcResult r = turn(Map.of("message", "분위기를 오로라처럼 몽환적으로 만들어줘", "revision", rev));
    long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(504);
    JsonNode b = objectMapper.readTree(r.getResponse().getContentAsString());
    assertThat(b.get("code").asText()).isEqualTo("AI_TIMEOUT");
    assertThat(b.has("edited")).as("성공/수정 응답 형태가 아니다").isFalse();
    assertThat(b.get("message").asText()).contains("입력과 작업본은 그대로");
    assertThat(elapsedMs).isLessThan(4500);
    assertThat(workingCopyJson()).isEqualTo(wcBefore);
    assertThat(liveJson()).isEqualTo(liveBefore);
    assertThat(allMessages()).isEqualTo(messagesBefore);
    assertThat(interrupted.get()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("given AI가 한도보다 오래 걸림·규칙으로 풀 수 있는 요청, when 수정 턴, then 규칙으로 몰래 처리하지 않고 504로 끝나며 아무것도 바뀌지 않는다")
  void slowAiDoesNotFallBackToRuleEdit() throws Exception {
    // given
    loginAs(login());
    assertThat(turn(CREATE).getResponse().getStatus()).isEqualTo(200);
    String wcBefore = workingCopyJson();
    int messagesBefore = allMessages();
    hang.set(true);

    // when
    MvcResult r = turn(Map.of("message", "주색을 진한 파란색으로 바꿔줘", "revision", revision()));

    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(504);
    assertThat(workingCopyJson()).isEqualTo(wcBefore);
    assertThat(allMessages()).isEqualTo(messagesBefore);
    assertThat(editMessages()).isZero();
  }

  @Test
  @DisplayName("given 지연 직후, when 같은 요청을 AI가 정상일 때 다시 보내면, then 처리된다(재시도 가능)")
  void retryAfterTimeoutWorks() throws Exception {
    // given
    loginAs(login());
    assertThat(turn(CREATE).getResponse().getStatus()).isEqualTo(200);
    hang.set(true);
    turn(Map.of("message", "분위기를 오로라처럼 몽환적으로 만들어줘", "revision", revision()));
    hang.set(false);

    // when
    MvcResult r = turn(Map.of("message", "주색을 진한 초록색으로 바꿔줘", "revision", revision()));

    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(objectMapper.readTree(r.getResponse().getContentAsString()).get("edited").asBoolean()).isTrue();
  }
}
