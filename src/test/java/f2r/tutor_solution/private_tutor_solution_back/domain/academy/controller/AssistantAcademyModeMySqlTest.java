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
 * 화면 모드 분리(A안): 최초 AI 생성 학원 / 기존 방식 학원 / 'AI로 이어서 만들기'로 명시 전환한 학원.
 * 기존 학원은 대화 API 호출만으로 모드가 바뀌지 않고(409 NOT_ASSISTANT_ACADEMY), 명시 확인 후에만 전환되며,
 * 전환 전 공개본은 버전 기록에 보관된다. 실제 보안 필터 체인과 MySQL을 쓴다.
 */
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
@DisplayName("AI 제작/기존 학원 화면 모드 분리 — 최초 생성·기존·명시 전환 (MySQL 통합)")
class AssistantAcademyModeMySqlTest {
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

  @BeforeEach
  void stubAi() {
    subjectRepository.findByNameKo("영어").orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    org.mockito.Mockito.when(aiProvider.isReady()).thenReturn(true);
    org.mockito.Mockito.when(aiProvider.name()).thenReturn("scripted");
    org.mockito.Mockito.when(aiProvider.complete(any(), any())).thenReturn(AiProvider.AiCompletion.failed("no script"));
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


  private String login() { return "mode_" + UUID.randomUUID().toString().substring(0, 8); }

  @Test
  @DisplayName("given 대화로 최초 생성한 학원, when 모드·화면·수정 턴, then ASSISTANT 모드로 대화형 화면과 수정이 동작한다")
  void aiCreatedAcademyIsAssistantMode() throws Exception {
    // given
    loginAs(login());
    assertThat(turn(CREATE).getResponse().getStatus()).isEqualTo(200);

    // when / then
    assertThat(body(fetch(API + "/mode")).get("mode").asText()).isEqualTo("ASSISTANT");
    assertThat(body(fetch(API + "/mode")).get("canAdopt").asBoolean()).isFalse();
    assertThat(pageView()).isEqualTo("tutor/assistant_home");
    assertThat(kindCount("RESULT")).isEqualTo(1);
    long rev = revision();
    MvcResult edit = turn(Map.of("message", "주색을 진한 파란색으로 바꿔줘", "revision", rev));
    assertThat(edit.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("given 기존 방식 학원, when 대화 API 5종을 직접 호출, then 모두 409 NOT_ASSISTANT_ACADEMY이고 기록·작업본·공개 데이터·화면 모드가 그대로다")
  void legacyAcademyApiIs409AndStaysClassic() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    String liveBefore = liveJson();
    int copiesBefore = workingCopies();

    // when / then
    assertNotAssistant(fetch(API + "/state"));
    assertNotAssistant(turn(Map.of("message", "주색을 진한 파란색으로 바꿔줘", "revision", 1)));
    assertNotAssistant(postJson("/undo", "{\"revision\":1}"));
    assertNotAssistant(postJson("/publish", "{\"revision\":1}"));
    assertNotAssistant(upload());
    assertThat(messages()).isZero();
    assertThat(workingCopies()).isEqualTo(copiesBefore);
    assertThat(liveJson()).isEqualTo(liveBefore);
    assertThat(pageView()).isEqualTo("tutor/academy_builder");
    assertThat(body(fetch(API + "/mode")).get("mode").asText()).isEqualTo("CLASSIC");
    assertThat(body(fetch(API + "/mode")).get("canAdopt").asBoolean()).isTrue();
    verify(aiProvider, never()).complete(any(), any());
  }

  @Test
  @DisplayName("given 이전에 대화 기록(RESULT 없음)이 쌓인 기존 학원, when 화면·API, then 기존 빌더이고 API는 409다")
  void legacyAcademyWithStrayMessagesStaysClassic() throws Exception {
    // given — 예전에 대화 API가 직접 호출돼 TEXT/EDIT 기록만 남은 기존 학원
    loginAs(login());
    createLegacyAcademy();
    Long academyId = jdbc.queryForObject("SELECT academy_id FROM academies WHERE tutor_user_id = ?", Long.class, tutor.getUserId());
    jdbc.update("INSERT INTO academy_assistant_message (academy_id, role, kind, content) VALUES (?, 'USER', 'TEXT', 'x'), (?, 'ASSISTANT', 'EDIT', 'y')", academyId, academyId);

    // when / then
    assertThat(pageView()).isEqualTo("tutor/academy_builder");
    assertNotAssistant(turn(Map.of("message", "주색을 진한 파란색으로 바꿔줘", "revision", 1)));
    assertThat(body(fetch(API + "/mode")).get("mode").asText()).isEqualTo("CLASSIC");
  }

  @Test
  @DisplayName("given 기존 방식 학원, when 확인 없이 전환 요청, then 400 CONFIRM_REQUIRED이고 아무것도 바뀌지 않는다")
  void adoptWithoutConfirmIsRejected() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    int versionsBefore = versions();

    // when
    MvcResult none = postJson("/adopt", "{}");
    MvcResult no = postJson("/adopt", "{\"confirm\":false}");

    // then
    assertThat(none.getResponse().getStatus()).isEqualTo(400);
    assertThat(body(none).get("code").asText()).isEqualTo("CONFIRM_REQUIRED");
    assertThat(no.getResponse().getStatus()).isEqualTo(400);
    assertThat(kindCount("ADOPTED")).isZero();
    assertThat(versions()).isEqualTo(versionsBefore);
    assertThat(pageView()).isEqualTo("tutor/academy_builder");
  }

  @Test
  @DisplayName("given 기존 방식 학원, when 확인하고 전환, then 공개본은 그대로·버전 기록에 보관·ADOPTED 기록·대화형 화면으로 바뀌고 수정이 작업본에만 닿는다")
  void adoptConvertsExplicitlyAndPreservesPublicVersion() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    String liveBefore = liveJson();
    int versionsBefore = versions();

    // when
    MvcResult adopt = postJson("/adopt", "{\"confirm\":true}");

    // then
    assertThat(adopt.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(adopt).get("adopted").asBoolean()).isTrue();
    long versionId = body(adopt).get("versionId").asLong();
    assertThat(liveJson()).isEqualTo(liveBefore);
    assertThat(versions()).isEqualTo(versionsBefore + 1);
    assertThat(jdbc.queryForObject("SELECT note FROM academy_page_versions WHERE id = ?", String.class, versionId))
        .isEqualTo("AI로 이어서 만들기 전 공개본");
    assertThat(jdbc.queryForObject("SELECT builder_draft_json FROM academy_page_versions WHERE id = ?", String.class, versionId))
        .contains("#112233");
    assertThat(kindCount("ADOPTED")).isEqualTo(1);
    assertThat(pageView()).isEqualTo("tutor/assistant_home");
    assertThat(body(fetch(API + "/mode")).get("mode").asText()).isEqualTo("ASSISTANT");

    // 전환 후 수정은 작업본에만 닿는다
    MvcResult edit = turn(Map.of("message", "주색을 진한 초록색으로 바꿔줘", "revision", revision()));
    assertThat(edit.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(edit).get("edited").asBoolean()).isTrue();
    assertThat(liveJson()).isEqualTo(liveBefore);
  }

  @Test
  @DisplayName("given 이미 전환된 학원, when 다시 전환, then 변경 없이 adopted=false이고 버전·기록이 늘지 않는다")
  void adoptIsIdempotent() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    assertThat(postJson("/adopt", "{\"confirm\":true}").getResponse().getStatus()).isEqualTo(200);
    int versionsAfterFirst = versions();

    // when
    MvcResult again = postJson("/adopt", "{\"confirm\":true}");

    // then
    assertThat(again.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(again).get("adopted").asBoolean()).isFalse();
    assertThat(versions()).isEqualTo(versionsAfterFirst);
    assertThat(kindCount("ADOPTED")).isEqualTo(1);
  }

  @Test
  @DisplayName("given 전환 후 수정·게시, when 보존된 버전을 불러와 다시 게시하면, then 전환 전 공개 값으로 복구된다")
  void publishedChangesCanBeRestoredFromPreservedVersion() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    String originalDraft = jdbc.queryForObject(
        "SELECT JSON_UNQUOTE(JSON_EXTRACT(page_settings, '$.builderDraft.colorPrimary')) FROM academies WHERE tutor_user_id = ?",
        String.class, tutor.getUserId());
    assertThat(originalDraft).isEqualTo("#112233");
    long versionId = body(postJson("/adopt", "{\"confirm\":true}")).get("versionId").asLong();
    assertThat(turn(Map.of("message", "주색을 진한 초록색으로 바꿔줘", "revision", revision())).getResponse().getStatus()).isEqualTo(200);
    assertThat(postJson("/publish", "{\"revision\":" + revision() + "}").getResponse().getStatus()).isEqualTo(200);
    String publishedColor = jdbc.queryForObject(
        "SELECT JSON_UNQUOTE(JSON_EXTRACT(page_settings, '$.builderDraft.colorPrimary')) FROM academies WHERE tutor_user_id = ?",
        String.class, tutor.getUserId());
    assertThat(publishedColor).isNotEqualTo("#112233");

    // when — 보존된 버전을 작업본에 불러오고 게시한다
    MvcResult restore = mockMvc.perform(post("/api/tutor/academy/working-copy/restore").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"revision\":" + revision() + ",\"versionId\":" + versionId + "}")).andReturn();
    authenticate();
    assertThat(restore.getResponse().getStatus()).isEqualTo(200);
    assertThat(postJson("/publish", "{\"revision\":" + revision() + "}").getResponse().getStatus()).isEqualTo(200);

    // then
    String restoredColor = jdbc.queryForObject(
        "SELECT JSON_UNQUOTE(JSON_EXTRACT(page_settings, '$.builderDraft.colorPrimary')) FROM academies WHERE tutor_user_id = ?",
        String.class, tutor.getUserId());
    assertThat(restoredColor).isEqualTo("#112233");
  }

  @Test
  @DisplayName("given 전환 후 아직 게시 전, when 작업본 되돌리기(discard), then 작업본이 공개본과 같아지고 공개본은 내내 불변이다")
  void discardRestoresWorkingCopyBeforePublish() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    String liveBefore = liveJson();
    postJson("/adopt", "{\"confirm\":true}");
    turn(Map.of("message", "주색을 진한 초록색으로 바꿔줘", "revision", revision()));

    // when
    MvcResult discard = mockMvc.perform(post("/api/tutor/academy/working-copy/discard").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + revision() + "}")).andReturn();
    authenticate();

    // then
    assertThat(discard.getResponse().getStatus()).isEqualTo(200);
    assertThat(liveJson()).isEqualTo(liveBefore);
    JsonNode wc = body(fetch("/api/tutor/academy/working-copy"));
    assertThat(wc.toString()).contains("#112233").doesNotContain("#16a34a");
  }

  @Test
  @DisplayName("given 학원이 없는 강사, when 전환 요청, then 400이고 학원이 생기지 않는다")
  void adoptWithoutAcademyIsRejected() throws Exception {
    // given
    loginAs(login());

    // when
    MvcResult r = postJson("/adopt", "{\"confirm\":true}");

    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(academies()).isZero();
  }

  @Test
  @DisplayName("given 비로그인, when 전환·모드 조회, then 401/403이다")
  void anonymousCannotAdoptOrReadMode() throws Exception {
    // given / when
    int adopt = mockMvc.perform(post(API + "/adopt").contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
        .andReturn().getResponse().getStatus();
    int mode = mockMvc.perform(get(API + "/mode")).andReturn().getResponse().getStatus();

    // then
    assertThat(adopt).isIn(401, 403);
    assertThat(mode).isIn(401, 403);
  }
}
