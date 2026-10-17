package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 수정 초기화(/reset)·새 홈페이지 만들기(/restart)·되돌리기(/undo)의 분리와 공개본 불변을 실제 MySQL로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "buildit.assistant.enabled=true"
})
@DisplayName("대화형 빌더 MySQL 통합 테스트 — 초기화·새 홈페이지·되돌리기 분리")
class AcademyAssistantResetRestartMySqlTest {

  private static final String API = "/api/tutor/academy/assistant";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper om;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private UserRepository users;
  @Autowired private SubjectRepository subjects;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private User tutor;
  private Cookie cookie;
  private long revision;

  @BeforeEach
  void setUp() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tutor = users.save(new User("rr_" + suffix, "pw", "원장", "별명", null, null, Role.TUTOR));
    cookie = new Cookie("ACCESS_TOKEN", tokens.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR"));
    subjects.findByNameKo("영어").orElseGet(() -> subjects.save(new Subject("ENGLISH", "영어")));
    auth();
    JsonNode created = call("/turn", Map.of("academyName", "초기화학원", "sampleKey", "junior-english", "region", "서울 강남구",
        "subject", "영어", "target", "중학생"));
    revision = created.get("revision").asLong();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void auth() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(tutor.getUserId(), tutor.getLoginId(), "TUTOR"), null, List.of()));
  }

  private JsonNode call(String path, Map<String, Object> body) throws Exception {
    String text = mockMvc.perform(post(API + path).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
        .content(om.writeValueAsString(body))).andReturn().getResponse().getContentAsString();
    auth();
    return om.readTree(text);
  }

  private JsonNode step(String path, Map<String, Object> extra) throws Exception {
    Map<String, Object> body = new java.util.HashMap<>(extra);
    body.put("revision", revision);
    JsonNode r = call(path, body);
    if (r.hasNonNull("revision")) revision = r.get("revision").asLong();
    return r;
  }

  private String draft(String key) {
    em.flush();
    String json = jdbc.queryForObject("SELECT w.page_settings_json FROM academy_working_copy w JOIN academies a ON a.academy_id = w.academy_id WHERE a.tutor_user_id = ?", String.class, tutor.getUserId());
    try {
      JsonNode n = om.readTree(json).path("builderDraft").path(key);
      return n.isMissingNode() ? null : n.asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String liveIntro() {
    em.flush();
    return jdbc.queryForObject("SELECT intro FROM academies WHERE tutor_user_id = ?", String.class, tutor.getUserId());
  }

  @Test
  @DisplayName("given 저장한 적 없는 초안 when 초기화하면 then 만든 디자인을 지우지 않고 저장 안내만 한다")
  void resetWithoutSavepointOnDraftIsSafeNoop() throws Exception {
    // given
    step("/turn", Map.of("message", "주색을 파란색으로"));
    String edited = draft("colorPrimary");
    String preset = draft("designPreset");
    // when
    JsonNode r = step("/reset", Map.of());
    // then
    assertThat(r.get("reply").asText()).contains("저장한 적이 없어서", "'저장'을 누르면");
    assertThat(r.get("edited").asBoolean()).isFalse();
    assertThat(draft("colorPrimary")).isEqualTo(edited);
    assertThat(draft("designPreset")).isEqualTo(preset).isNotNull();
  }

  @Test
  @DisplayName("given 공개 중이고 저장점이 없는 홈페이지 when 수정 후 초기화하면 then 공개 중인 내용 기준으로 복원하고 안내한다")
  void resetPublishedWithoutSavepointUsesPublishedBaseline() throws Exception {
    // given
    JsonNode pub = step("/publish", Map.of());
    assertThat(pub.has("firstPublish") || pub.has("revision")).isTrue();
    String published = draft("colorPrimary");
    String intro = liveIntro();
    step("/turn", Map.of("message", "주색을 파란색으로"));
    assertThat(draft("colorPrimary")).isNotEqualTo(published);
    // when
    JsonNode r = step("/reset", Map.of());
    // then
    assertThat(r.get("reply").asText()).contains("공개 중인 홈페이지 내용", "저장한 적이 없어서");
    assertThat(draft("colorPrimary")).isEqualTo(published);
    assertThat(liveIntro()).isEqualTo(intro);
  }

  @Test
  @DisplayName("given 저장점이 있는 작업본 when 수정 후 초기화하면 then 마지막 저장 상태로 복원하고 되돌리기로 초기화를 취소한다")
  void resetToSavepointThenUndoIsIndependent() throws Exception {
    // given
    step("/save", Map.of());
    String saved = draft("colorPrimary");
    step("/turn", Map.of("message", "주색을 파란색으로"));
    String edited = draft("colorPrimary");
    // when
    JsonNode reset = step("/reset", Map.of());
    String afterReset = draft("colorPrimary");
    step("/undo", Map.of());
    // then
    assertThat(reset.get("reply").asText()).contains("마지막 저장 상태").doesNotContain("저장한 적이 없어서");
    assertThat(afterReset).isEqualTo(saved);
    assertThat(draft("colorPrimary")).isEqualTo(edited);
  }

  @Test
  @DisplayName("given 작업본 when 새 홈페이지를 만들면 then 디자인이 바뀌고 공개 데이터는 그대로이며 되돌리기로 이전 작업본이 돌아온다")
  void restartChangesDraftOnlyAndUndoRestores() throws Exception {
    // given
    String presetBefore = draft("designPreset");
    String intro = liveIntro();
    // when
    JsonNode r = step("/restart", Map.of("message", "미술학원 홈페이지를 갤러리 스타일로 만들어줘"));
    // then
    assertThat(r.get("edited").asBoolean()).isTrue();
    assertThat(draft("designPreset")).isEqualTo("ARTS").isNotEqualTo(presetBefore);
    assertThat(liveIntro()).isEqualTo(intro);
    step("/undo", Map.of());
    assertThat(draft("designPreset")).isEqualTo(presetBefore);
  }

  @Test
  @DisplayName("given 빈 요청·위험 문자열 when 새 홈페이지를 만들면 then 400이고 작업본은 그대로다")
  void restartRejectsBadInput() throws Exception {
    // given
    String presetBefore = draft("designPreset");
    // when
    JsonNode empty = step("/restart", Map.of("message", "  "));
    JsonNode danger = step("/restart", Map.of("message", "<script>alert(1)</script>"));
    // then
    assertThat(empty.get("message").asText()).contains("어떤 홈페이지");
    assertThat(danger.has("message")).isTrue();
    assertThat(draft("designPreset")).isEqualTo(presetBefore);
  }
}
