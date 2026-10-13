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
 * 게시 후 버전 복원이 선택한 버전과 실제로 일치하는지 검증한다(복원 전후 비교).
 * builderDraft는 버전에 있는 키만 남고(수정으로 새로 생긴 키는 제거), 버전이 기록한 섹션 표시·순서도 되돌아온다.
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
@DisplayName("게시 후 버전 복원 — 선택한 버전과 복원 결과 일치 (MySQL 통합)")
class AssistantVersionRestoreMySqlTest {
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
        "pageSettings", Map.of("builderDraft", Map.of("designPreset", "EXAM", "colorPrimary", "#112233"),
            "sectionVisibility", Map.of("sec-videos", true))));
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



  private String login() { return "rst_" + UUID.randomUUID().toString().substring(0, 8); }

  private JsonNode liveSettings() throws Exception {
    em.flush();
    String json = jdbc.queryForObject("SELECT CAST(page_settings AS CHAR) FROM academies WHERE tutor_user_id = ?", String.class, tutor.getUserId());
    return objectMapper.readTree(json);
  }

  private JsonNode workingSettings() throws Exception {
    JsonNode wc = body(fetch("/api/tutor/academy/working-copy"));
    return wc.get("pageSettings");
  }

  private JsonNode draftOf(JsonNode settings) {
    JsonNode d = settings.get("builderDraft");
    com.fasterxml.jackson.databind.node.ObjectNode copy = objectMapper.createObjectNode();
    if (d != null && d.isObject()) {
      d.fields().forEachRemaining(e -> { if (!"homepage.renderMode".equals(e.getKey())) copy.set(e.getKey(), e.getValue()); });
    }
    return copy;
  }

  private void say(String text) throws Exception {
    MvcResult r = turn(Map.of("message", text, "revision", revision()));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).get("edited").asBoolean()).as(text).isTrue();
  }

  private void restore(long versionId) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/tutor/academy/working-copy/restore").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"revision\":" + revision() + ",\"versionId\":" + versionId + "}")).andReturn();
    authenticate();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
  }

  private void publish() throws Exception {
    assertThat(postJson("/publish", "{\"revision\":" + revision() + "}").getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("given 전환 후 색 변경·섹션 숨김·새 키(motionStyle) 추가 뒤 게시, when 보존된 버전을 복원하면, then 작업본 builderDraft·섹션 표시가 버전과 정확히 같고 새 키는 사라진다")
  void restoreMatchesSelectedVersionExactly() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    JsonNode draftBefore = draftOf(liveSettings());
    JsonNode visibilityBefore = liveSettings().get("sectionVisibility");
    assertThat(draftBefore.fieldNames()).toIterable().containsExactlyInAnyOrder("designPreset", "colorPrimary");
    long versionId = body(postJson("/adopt", "{\"confirm\":true}")).get("versionId").asLong();
    say("주색을 진한 초록색으로 바꿔줘");
    say("수업 영상 섹션은 숨겨줘");
    say("카드들이 스크롤할 때 아래에서 차례대로 올라오게 해줘");
    publish();
    JsonNode draftAfterPublish = draftOf(liveSettings());
    assertThat(draftAfterPublish.has("motionStyle")).as("수정으로 새 키가 생김").isTrue();
    assertThat(draftAfterPublish).isNotEqualTo(draftBefore);

    // when — 복원(작업본에만 불러옴)
    restore(versionId);

    // then — 작업본이 버전과 정확히 일치(새 키 제거), 공개본은 게시 전까지 그대로
    JsonNode restoredDraft = draftOf(workingSettings());
    assertThat(restoredDraft).as("복원 후 작업본 builderDraft == 버전").isEqualTo(draftBefore);
    assertThat(restoredDraft.has("motionStyle")).isFalse();
    assertThat(workingSettings().get("sectionVisibility")).as("섹션 표시도 버전 값").isEqualTo(visibilityBefore);
    assertThat(draftOf(liveSettings())).as("게시 전 공개본 불변").isEqualTo(draftAfterPublish);

    // when — 게시
    publish();

    // then — 공개본이 전환 전과 같다
    assertThat(draftOf(liveSettings())).isEqualTo(draftBefore);
    assertThat(liveSettings().get("sectionVisibility")).isEqualTo(visibilityBefore);
  }

  @Test
  @DisplayName("given 페이지 설정이 비어 있는 옛 버전(AI 생성 버전), when 복원하면, then builderDraft만 정확히 버전과 같고 섹션 표시 등 기록되지 않은 항목은 현재 값을 유지한다")
  void versionWithoutRecordedSettingsKeepsCurrentNonDraftSettings() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    postJson("/adopt", "{\"confirm\":true}");
    say("수업 영상 섹션은 숨겨줘");
    say("카드들이 스크롤할 때 아래에서 차례대로 올라오게 해줘");
    Long academyId = jdbc.queryForObject("SELECT academy_id FROM academies WHERE tutor_user_id = ?", Long.class, tutor.getUserId());
    jdbc.update("INSERT INTO academy_page_versions (academy_id, version_no, builder_draft_json, page_settings_json, created_by, note) VALUES (?, 99, ?, '{}', 'AI', 'AI 생성 홈페이지 최초 생성')",
        academyId, "{\"designPreset\":\"EXAM\"}");
    long oldVersionId = jdbc.queryForObject("SELECT id FROM academy_page_versions WHERE academy_id = ? AND version_no = 99", Long.class, academyId);
    JsonNode visibilityNow = workingSettings().get("sectionVisibility");

    // when
    restore(oldVersionId);

    // then
    JsonNode restored = workingSettings();
    assertThat(draftOf(restored)).isEqualTo(objectMapper.readTree("{\"designPreset\":\"EXAM\"}"));
    assertThat(restored.get("sectionVisibility")).as("기록 없는 항목은 현재 값 유지").isEqualTo(visibilityNow);
  }

  @Test
  @DisplayName("given 복원 요청, when 작업본 revision과 다르면, then 409이고 작업본이 바뀌지 않는다")
  void staleRevisionRestoreIsRejected() throws Exception {
    // given
    loginAs(login());
    createLegacyAcademy();
    long versionId = body(postJson("/adopt", "{\"confirm\":true}")).get("versionId").asLong();
    say("주색을 진한 초록색으로 바꿔줘");
    JsonNode before = workingSettings();

    // when
    MvcResult r = mockMvc.perform(post("/api/tutor/academy/working-copy/restore").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + (revision() + 7) + ",\"versionId\":" + versionId + "}")).andReturn();
    authenticate();

    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(workingSettings()).isEqualTo(before);
  }
}
