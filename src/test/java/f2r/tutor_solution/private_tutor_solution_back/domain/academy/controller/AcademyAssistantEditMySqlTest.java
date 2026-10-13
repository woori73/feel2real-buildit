package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider;
import jakarta.servlet.http.Cookie;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.function.Function;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt 통합 화면 2-A — 대화 턴·대화 기록·원클릭 게시 MySQL 통합 테스트 (실제 V1061 마이그레이션).
 *
 * <p>핵심 불변식: 생성은 작업본에만 쓰고, '게시하기' 전에는 공개 내용이 바뀌지 않으며,
 * 최초 게시에서만 DRAFT → PUBLISHED가 된다.
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
@DisplayName("대화 수정 턴 MySQL 통합 테스트 — 작업본만 수정·여러 단계 되돌리기·보호")
class AcademyAssistantEditMySqlTest {

  private static final String API = "/api/tutor/academy/assistant";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private User tutor;
  private Cookie authCookie;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tutor = userRepository.save(new User("as_" + suffix, "pw", "대화강사", "별명", null, null, Role.TUTOR));
    authCookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR"));
    subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    authenticate();
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

  private JsonNode json(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  private MvcResult turn(Map<String, Object> body) throws Exception {
    MvcResult r = mockMvc.perform(post(API + "/turn").cookie(authCookie)
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andReturn();
    authenticate();
    return r;
  }

  private MvcResult publish(long revision) throws Exception {
    MvcResult r = mockMvc.perform(post(API + "/publish").cookie(authCookie)
        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + revision + "}")).andReturn();
    authenticate();
    return r;
  }

  private int academyCount() {
    em.flush();
    return jdbc.queryForObject("SELECT COUNT(*) FROM academies WHERE tutor_user_id = ?", Integer.class, tutor.getUserId());
  }

  private String liveColumn(String column) {
    em.flush();
    return jdbc.queryForObject(
        "SELECT " + column + " FROM academies WHERE tutor_user_id = ?", String.class, tutor.getUserId());
  }

  private String workingCopyColumn(String column) {
    em.flush();
    return jdbc.queryForObject(
        "SELECT w." + column + " FROM academy_working_copy w JOIN academies a ON a.academy_id = w.academy_id "
            + "WHERE a.tutor_user_id = ?", String.class, tutor.getUserId());
  }

  private int messageCount() {
    em.flush();
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_assistant_message m JOIN academies a ON a.academy_id = m.academy_id "
            + "WHERE a.tutor_user_id = ?", Integer.class, tutor.getUserId());
  }

  private MvcResult createFromSample(String sampleKey) throws Exception {
    return turn(Map.of("academyName", "새봄영어학원", "sampleKey", sampleKey, "region", "서울 강남구",
        "subject", "영어", "target", "중학생"));
  }


  /** 테스트가 AI 응답을 정하는 제공자(Muse Spark 대신). */
  static final AtomicReference<Function<String, AiProvider.AiCompletion>> AI = new AtomicReference<>();

  @MockitoBean private AiProvider aiProvider;

  @BeforeEach
  void stubAi() {
    AI.set(p -> AiProvider.AiCompletion.failed("no script")); // 생성 단계는 AI 없이도 동작해야 한다
    when(aiProvider.isReady()).thenReturn(true);
    when(aiProvider.name()).thenReturn("scripted");
    when(aiProvider.complete(any(), any())).thenAnswer(inv -> AI.get().apply(inv.getArgument(0)));
  }

  private static AiProvider.AiCompletion ai(String json) {
    return AiProvider.AiCompletion.ok(json);
  }

  private MvcResult undo(long revision) throws Exception {
    MvcResult r = mockMvc.perform(post(API + "/undo").cookie(authCookie)
        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + revision + "}")).andReturn();
    authenticate();
    return r;
  }

  private JsonNode state() throws Exception {
    MvcResult r = mockMvc.perform(get(API + "/state").cookie(authCookie)).andReturn();
    authenticate();
    return json(r);
  }

  private JsonNode publicBody() throws Exception {
    String slug = liveColumn("slug");
    return objectMapper.readTree(mockMvc.perform(get("/api/academies/" + slug)).andReturn().getResponse().getContentAsString());
  }

  /** 생성 → 게시까지 해서 공개 홈페이지가 있는 상태로 만든다. 반환: 작업본 revision. */
  private long createAndPublish() throws Exception {
    JsonNode created = json(createFromSample("exam"));
    assertThat(created.get("created").asBoolean()).isTrue();
    JsonNode pub = json(publish(created.get("revision").asLong()));
    return pub.get("revision").asLong();
  }

  private JsonNode edit(String message, long revision) throws Exception {
    return json(turn(Map.of("message", message, "revision", revision)));
  }

  private static final String DARK_PREMIUM = "{\"reply\":\"고급스러운 다크 분위기로 바꿨어요.\",\"ops\":["
      + "{\"op\":\"setPreset\",\"value\":\"DARK\"},{\"op\":\"setTypography\",\"value\":\"PREMIUM\"},"
      + "{\"op\":\"setHeroLayout\",\"value\":\"center\"},{\"op\":\"setCardStyle\",\"value\":\"minimal\"}]}";

  @Test
  @DisplayName("given 게시된 홈페이지, when 포괄적 디자인 요청을 하면, then 복합 변경이 작업본에만 적용되고 공개 홈페이지는 그대로다")
  void compoundEditAppliesToWorkingCopyOnly() throws Exception {
    long rev = createAndPublish();
    JsonNode publicBefore = publicBody();
    String liveBefore = liveColumn("page_settings");
    AI.set(p -> ai(DARK_PREMIUM));

    JsonNode r = edit("전체적으로 더 고급스러운 분위기로 바꿔줘", rev);

    assertThat(r.get("edited").asBoolean()).isTrue();
    assertThat(r.get("engine").asText()).isEqualTo("AI:scripted");
    assertThat(r.get("changes").size()).isGreaterThanOrEqualTo(4);
    assertThat(r.get("revision").asLong()).isGreaterThan(rev);
    assertThat(r.get("undoDepth").asInt()).isEqualTo(1);
    assertThat(workingCopyColumn("page_settings_json")).contains("\"designPreset\":\"DARK\"").contains("PREMIUM");
    assertThat(liveColumn("page_settings")).isEqualTo(liveBefore);
    assertThat(publicBody()).isEqualTo(publicBefore);
  }

  @Test
  @DisplayName("given 연속 수정 5회(프리셋·색·문구·섹션 숨김·순서), when 5번 되돌리면, then 작업본이 최초 내용으로 돌아가고 6번째는 안내한다")
  void fiveEditsThenFiveUndos() throws Exception {
    long rev = createAndPublish();
    String wcBefore = workingCopyColumn("page_settings_json");
    String introBefore = workingCopyColumn("intro");
    String liveBefore = liveColumn("page_settings");
    String[] script = {
        "{\"reply\":\"1\",\"ops\":[{\"op\":\"setPreset\",\"value\":\"WARM\"}]}",
        "{\"reply\":\"2\",\"ops\":[{\"op\":\"setColor\",\"key\":\"colorPrimary\",\"value\":\"#0f4c81\"}]}",
        "{\"reply\":\"3\",\"ops\":[{\"op\":\"setText\",\"key\":\"introHeading\",\"value\":\"아이의 속도에 맞춰 함께 걷습니다\"}]}",
        "{\"reply\":\"4\",\"ops\":[{\"op\":\"hideSection\",\"id\":\"sec-videos\"}]}",
        "{\"reply\":\"5\",\"ops\":[{\"op\":\"moveSection\",\"id\":\"sec-lessons\",\"value\":\"first\"}]}"};
    String[] says = {"따뜻한 느낌으로", "주색을 진한 파랑으로", "소개 제목을 부드럽게", "수업 영상 섹션 숨겨줘", "수업 섹션을 맨 위로"};
    for (int i = 0; i < 5; i++) {
      final String s = script[i];
      AI.set(p -> ai(s));
      JsonNode r = edit(says[i], rev);
      assertThat(r.get("edited").asBoolean()).as(says[i]).isTrue();
      assertThat(r.get("undoDepth").asInt()).isEqualTo(i + 1);
      rev = r.get("revision").asLong();
    }
    String wcAfter = workingCopyColumn("page_settings_json");
    assertThat(wcAfter).isNotEqualTo(wcBefore).contains("WARM").contains("#0f4c81").contains("sec-videos");
    assertThat(liveColumn("page_settings")).isEqualTo(liveBefore);
    assertThat(state().get("undoDepth").asInt()).isEqualTo(5);

    for (int i = 5; i >= 1; i--) {
      JsonNode u = json(undo(rev));
      assertThat(u.get("failed").asBoolean()).as("undo " + i).isFalse();
      assertThat(u.get("undoDepth").asInt()).isEqualTo(i - 1);
      rev = u.get("revision").asLong();
    }
    assertThat(introBefore).isEqualTo(workingCopyColumn("intro"));
    // 표시·순서는 '저장 안 됨(null)'이 '기본값 전체'로 바뀔 수 있다(렌더 결과는 동일). 나머지는 그대로여야 한다.
    JsonNode restored = objectMapper.readTree(workingCopyColumn("page_settings_json"));
    JsonNode original = objectMapper.readTree(wcBefore);
    assertThat(restored.get("builderDraft")).isEqualTo(original.get("builderDraft"));
    assertThat(restored.get("sectionVisibility") == null || restored.get("sectionVisibility").isNull()
        || restored.get("sectionVisibility").size() == 0 || !restored.get("sectionVisibility").toString().contains("false")).isTrue();
    if (restored.hasNonNull("sectionOrder")) {
      assertThat(objectMapper.convertValue(restored.get("sectionOrder"), List.class))
          .isEqualTo(defaultOrder());
    }
    JsonNode extra = json(undo(rev));
    assertThat(extra.get("failed").asBoolean()).isTrue();
    assertThat(extra.get("reply").asText()).contains("되돌릴 수정이 없어요");
  }

  private List<String> defaultOrder() throws Exception {
    java.lang.reflect.Field f = f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService.class.getDeclaredField("DEFAULT_SECTION_ORDER");
    f.setAccessible(true);
    @SuppressWarnings("unchecked") List<String> v = (List<String>) f.get(null);
    return v;
  }

  /** JSON 키 순서·공백 차이를 무시하고 비교하기 위한 정규화. */
  private String canon(String json) throws Exception {
    return objectMapper.writeValueAsString(objectMapper.treeToValue(objectMapper.readTree(json), Object.class));
  }

  @Test
  @DisplayName("given AI가 잘못된 patch(허용 밖 값·가짜 전화번호·잘못된 hex)를 낼 때, then 모두 거부되고 작업본은 그대로다")
  void invalidPatchIsRejected() throws Exception {
    long rev = createAndPublish();
    String wcBefore = workingCopyColumn("page_settings_json");
    AI.set(p -> ai("{\"reply\":\"바꿨어요\",\"ops\":[{\"op\":\"setPreset\",\"value\":\"NEON\"},"
        + "{\"op\":\"setColor\",\"key\":\"colorPrimary\",\"value\":\"blue\"},"
        + "{\"op\":\"setText\",\"key\":\"introMotto\",\"value\":\"문의 010-1234-5678\"},"
        + "{\"op\":\"setText\",\"key\":\"password\",\"value\":\"x\"},"
        + "{\"op\":\"hideSection\",\"id\":\"sec-hero\"}]}"));

    JsonNode r = edit("전부 바꿔줘", rev);

    assertThat(r.get("edited").asBoolean()).isFalse();
    assertThat(r.get("skipped").size()).isEqualTo(5);
    assertThat(r.get("reply").asText()).contains("작업본은 그대로");
    assertThat(workingCopyColumn("page_settings_json")).isEqualTo(wcBefore);
    assertThat(r.get("revision").asLong()).isEqualTo(rev);
  }

  @Test
  @DisplayName("given AI 응답 실패, when 규칙으로 해석 가능한 요청이면, then 규칙 폴백으로 적용하고 이해 못 할 요청이면 작업본을 그대로 둔다")
  void aiFailureFallsBackAndPreservesWorkingCopy() throws Exception {
    long rev = createAndPublish();
    AI.set(p -> AiProvider.AiCompletion.failed("timeout"));

    JsonNode unclear = edit("음 뭔가 바꿔줘", rev);
    assertThat(unclear.get("edited").asBoolean()).isFalse();
    assertThat(unclear.get("question").asText()).isNotBlank();
    assertThat(unclear.get("reply").asText()).contains("작업본은 그대로");
    assertThat(unclear.get("revision").asLong()).isEqualTo(rev);

    JsonNode ruled = edit("수업 영상 섹션 숨겨 줘", rev);
    assertThat(ruled.get("edited").asBoolean()).isTrue();
    assertThat(ruled.get("engine").asText()).isEqualTo("RULE");
    assertThat(workingCopyColumn("page_settings_json")).contains("\"sec-videos\":false");
  }

  @Test
  @DisplayName("given 깨진 AI 응답(비JSON), when 해석 가능한 요청이면, then 규칙 폴백으로 처리한다")
  void malformedAiResponseFallsBack() throws Exception {
    long rev = createAndPublish();
    AI.set(p -> ai("죄송하지만 JSON을 만들 수 없어요"));
    JsonNode r = edit("더 고급스럽게 바꿔줘", rev);
    assertThat(r.get("edited").asBoolean()).isTrue();
    assertThat(r.get("engine").asText()).isEqualTo("RULE");
  }

  @Test
  @DisplayName("given 오래된 revision(다른 탭에서 수정됨), when 수정·되돌리기를 요청하면, then 409 REVISION_CONFLICT이고 작업본은 그대로다")
  void staleRevisionConflicts() throws Exception {
    long rev = createAndPublish();
    AI.set(p -> ai(DARK_PREMIUM));
    JsonNode first = edit("고급스럽게", rev);
    String wcAfterFirst = workingCopyColumn("page_settings_json");

    MvcResult stale = turn(Map.of("message", "더 밝게", "revision", rev));
    assertThat(stale.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(stale).get("code").asText()).isEqualTo("REVISION_CONFLICT");
    MvcResult staleUndo = undo(rev);
    assertThat(staleUndo.getResponse().getStatus()).isEqualTo(409);

    assertThat(workingCopyColumn("page_settings_json")).isEqualTo(wcAfterFirst);
    assertThat(first.get("revision").asLong()).isGreaterThan(rev);
  }

  @Test
  @DisplayName("given 수정 후 작업본이 다른 경로로 바뀜, when 되돌리기하면, then 안전하게 거부하고 작업본을 건드리지 않는다")
  void undoRefusedWhenWorkingCopyChangedElsewhere() throws Exception {
    long rev = createAndPublish();
    AI.set(p -> ai(DARK_PREMIUM));
    long afterEdit = edit("고급스럽게", rev).get("revision").asLong();
    // 회원이 다른 경로(작업본 API)로 직접 수정
    MvcResult manual = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .put("/api/tutor/academy/working-copy").cookie(authCookie).contentType(MediaType.APPLICATION_JSON)
        .content("{\"revision\":" + afterEdit + ",\"intro\":\"직접 바꾼 소개\"}")).andReturn();
    authenticate();
    long manualRev = json(manual).get("revision").asLong();
    String wcBefore = workingCopyColumn("page_settings_json");

    JsonNode u = json(undo(manualRev));

    assertThat(u.get("failed").asBoolean()).isTrue();
    assertThat(u.get("reply").asText()).contains("안전하게 되돌릴 수 없어요");
    assertThat(workingCopyColumn("page_settings_json")).isEqualTo(wcBefore);
    assertThat(workingCopyColumn("intro")).isEqualTo("직접 바꾼 소개");
  }

  @Test
  @DisplayName("given 수정 후 게시, when 게시하면, then 공개가 바뀌고 되돌리기 단계는 0이 되며 게시 전 수정은 되돌릴 수 없다")
  void publishAfterEditsResetsUndoStack() throws Exception {
    long rev = createAndPublish();
    AI.set(p -> ai(DARK_PREMIUM));
    JsonNode e = edit("고급스럽게", rev);
    assertThat(publicBody().get("pageSettings").get("builderDraft").has("typographyPreset")).isFalse();

    JsonNode pub = json(publish(e.get("revision").asLong()));

    assertThat(publicBody().get("pageSettings").get("builderDraft").get("designPreset").asText()).isEqualTo("DARK");
    assertThat(state().get("undoDepth").asInt()).isZero();
    JsonNode u = json(undo(pub.get("revision").asLong()));
    assertThat(u.get("failed").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("given 되묻기 응답, when 요청이 모호하면, then 질문을 돌려주고 작업본은 그대로다")
  void questionKeepsWorkingCopy() throws Exception {
    long rev = createAndPublish();
    String wcBefore = workingCopyColumn("page_settings_json");
    AI.set(p -> ai("{\"reply\":null,\"question\":\"어떤 색 계열을 원하시나요?\",\"ops\":[]}"));

    JsonNode r = edit("색 좀 바꿔줘", rev);

    assertThat(r.get("edited").asBoolean()).isFalse();
    assertThat(r.get("question").asText()).contains("어떤 색");
    assertThat(workingCopyColumn("page_settings_json")).isEqualTo(wcBefore);
  }

  @Test
  @DisplayName("given STUDENT·비로그인, when 수정·되돌리기를 호출하면, then 거부된다(기존 권한 불변)")
  void nonTutorsAreRejected() throws Exception {
    User student = userRepository.save(new User("st_" + UUID.randomUUID().toString().substring(0, 8), "pw", "학생", "학생", null, null, Role.STUDENT));
    Cookie studentCookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(student.getUserId(), student.getLoginId(), "STUDENT"));
    for (String path : new String[] {"/turn", "/undo"}) {
      assertThat(mockMvc.perform(post(API + path).cookie(studentCookie).contentType(MediaType.APPLICATION_JSON)
          .content("{\"message\":\"x\",\"revision\":1}")).andReturn().getResponse().getStatus()).isEqualTo(403);
      assertThat(mockMvc.perform(post(API + path).contentType(MediaType.APPLICATION_JSON)
          .content("{\"message\":\"x\",\"revision\":1}")).andReturn().getResponse().getStatus()).isIn(401, 403);
    }
  }
}
