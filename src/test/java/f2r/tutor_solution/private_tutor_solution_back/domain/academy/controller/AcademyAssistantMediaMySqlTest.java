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
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
@DisplayName("사진 올리기·삭제 MySQL 통합 테스트 — 작업본 예약·게시 때만 공개 반영·되돌리기")
class AcademyAssistantMediaMySqlTest {

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


  private static byte[] png(int rgb) throws Exception {
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) img.setRGB(x, y, rgb);
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", out);
    return out.toByteArray();
  }

  private MvcResult uploadPhoto(String slot, byte[] bytes, long revision) throws Exception {
    MvcResult r = mockMvc.perform(multipart(API + "/media")
        .file(new MockMultipartFile("image", "p.png", "image/png", bytes))
        .param("slot", slot).param("revision", String.valueOf(revision)).cookie(authCookie)).andReturn();
    authenticate();
    return r;
  }

  private int previewMediaStatus(String slot) throws Exception {
    int s = mockMvc.perform(get(API + "/media/" + slot).cookie(authCookie)).andReturn().getResponse().getStatus();
    authenticate();
    return s;
  }

  private byte[] previewMediaBytes(String slot) throws Exception {
    byte[] b = mockMvc.perform(get(API + "/media/" + slot).cookie(authCookie)).andReturn().getResponse().getContentAsByteArray();
    authenticate();
    return b;
  }

  private int publicMediaStatus(String slot) throws Exception {
    return mockMvc.perform(get("/api/academies/" + liveColumn("slug") + "/hero-media/" + slot)).andReturn().getResponse().getStatus();
  }

  private JsonNode previewBody() throws Exception {
    MvcResult r = mockMvc.perform(get("/api/tutor/academy/working-copy/preview").cookie(authCookie)).andReturn();
    authenticate();
    return json(r);
  }

  private String stagedJson() {
    em.flush();
    return jdbc.queryForObject(
        "SELECT w.staged_media_json FROM academy_working_copy w JOIN academies a ON a.academy_id = w.academy_id WHERE a.tutor_user_id = ?",
        String.class, tutor.getUserId());
  }

  @Test
  @DisplayName("given 게시된 홈페이지, when 원장 사진을 올리면, then 작업본에만 예약되고 공개 슬롯·공개 이미지는 그대로이며 미리보기엔 보인다")
  void uploadStagesInWorkingCopyOnly() throws Exception {
    long rev = createAndPublish();
    String liveBefore = liveColumn("principal_photo_storage_key");
    assertThat(publicMediaStatus("principal")).isEqualTo(404);

    JsonNode r = json(uploadPhoto("principal", png(0xff0000), rev));

    assertThat(r.get("edited").asBoolean()).isTrue();
    assertThat(r.get("changes").get(0).get("label").asText()).isEqualTo("사진 추가: 원장 사진");
    assertThat(r.get("undoDepth").asInt()).isEqualTo(1);
    assertThat(r.get("revision").asLong()).isGreaterThan(rev);
    assertThat(liveColumn("principal_photo_storage_key")).isEqualTo(liveBefore).isNull();
    assertThat(stagedJson()).contains("\"principal\":\"");
    assertThat(publicMediaStatus("principal")).isEqualTo(404);
    assertThat(previewMediaStatus("principal")).isEqualTo(200);
    assertThat(previewBody().get("heroMediaSlots").toString()).contains("principal");
    assertThat(state().get("dirty").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("given 사진을 올린 뒤, when 되돌리면, then 예약이 사라져 공개본 그대로로 돌아간다")
  void undoRemovesStagedPhoto() throws Exception {
    long rev = createAndPublish();
    JsonNode up = json(uploadPhoto("principal", png(0x00ff00), rev));

    JsonNode u = json(undo(up.get("revision").asLong()));

    assertThat(u.get("failed").asBoolean()).isFalse();
    assertThat(stagedJson()).isNull();
    assertThat(previewMediaStatus("principal")).isEqualTo(404);
    assertThat(previewBody().get("heroMediaSlots").toString()).doesNotContain("principal");
  }

  @Test
  @DisplayName("given 사진을 두 번 교체, when 한 번 되돌리면, then 첫 번째 사진으로 돌아간다(파일 보존)")
  void undoRestoresPreviousPhoto() throws Exception {
    long rev = createAndPublish();
    byte[] first = png(0xff0000);
    JsonNode a = json(uploadPhoto("principal", first, rev));
    byte[] firstServed = previewMediaBytes("principal");
    JsonNode b = json(uploadPhoto("principal", png(0x0000ff), a.get("revision").asLong()));
    assertThat(b.get("changes").get(0).get("label").asText()).isEqualTo("사진 교체: 원장 사진");
    assertThat(previewMediaBytes("principal")).isNotEqualTo(firstServed);

    json(undo(b.get("revision").asLong()));

    assertThat(previewMediaBytes("principal")).isEqualTo(firstServed);
    assertThat(state().get("undoDepth").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 예약된 사진, when 게시하면, then 그때만 공개 슬롯에 반영되고 예약·되돌리기 단계는 비워진다")
  void publishPromotesStagedPhoto() throws Exception {
    long rev = createAndPublish();
    JsonNode up = json(uploadPhoto("principal", png(0xff00ff), rev));
    assertThat(publicMediaStatus("principal")).isEqualTo(404);

    JsonNode pub = json(publish(up.get("revision").asLong()));

    assertThat(pub.get("revision").asLong()).isGreaterThan(up.get("revision").asLong());
    assertThat(liveColumn("principal_photo_storage_key")).isNotBlank();
    assertThat(publicMediaStatus("principal")).isEqualTo(200);
    assertThat(stagedJson()).isNull();
    assertThat(state().get("undoDepth").asInt()).isZero();
    assertThat(state().get("dirty").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("given 공개된 사진, when 대화로 '원장 사진 지워줘'(AI clearImage), then 작업본에서만 비워지고 게시하면 공개에서 사라진다")
  void clearViaConversation() throws Exception {
    long rev = createAndPublish();
    JsonNode up = json(uploadPhoto("principal", png(0x123456), rev));
    JsonNode pub = json(publish(up.get("revision").asLong()));
    assertThat(publicMediaStatus("principal")).isEqualTo(200);
    AI.set(p -> ai("{\"reply\":\"원장 사진을 지웠어요.\",\"ops\":[{\"op\":\"clearImage\",\"value\":\"principal\"}]}"));

    JsonNode r = edit("원장 사진 지워줘", pub.get("revision").asLong());

    assertThat(r.get("edited").asBoolean()).isTrue();
    assertThat(r.get("changes").get(0).get("label").asText()).isEqualTo("사진 삭제: 원장 사진");
    assertThat(stagedJson()).contains("\"principal\":\"\"");
    assertThat(publicMediaStatus("principal")).isEqualTo(200);
    assertThat(previewMediaStatus("principal")).isEqualTo(404);

    JsonNode u = json(undo(r.get("revision").asLong()));
    assertThat(u.get("failed").asBoolean()).isFalse();
    assertThat(stagedJson()).isNull();
    assertThat(previewMediaStatus("principal")).isEqualTo(200);

    JsonNode r2 = edit("원장 사진 지워줘", u.get("revision").asLong());
    json(publish(r2.get("revision").asLong()));
    assertThat(liveColumn("principal_photo_storage_key")).isNull();
    assertThat(publicMediaStatus("principal")).isEqualTo(404);
  }

  @Test
  @DisplayName("given 사진이 없는 자리, when 지우라고 하면, then 아무것도 바꾸지 않고 이미 비어 있다고 안내한다")
  void clearEmptySlotChangesNothing() throws Exception {
    long rev = createAndPublish();
    AI.set(p -> ai("{\"reply\":\"지웠어요.\",\"ops\":[{\"op\":\"clearImage\",\"value\":\"schedule\"}]}"));

    JsonNode r = edit("시간표 달력 지워줘", rev);

    assertThat(r.get("edited").asBoolean()).isFalse();
    assertThat(r.get("skipped").toString()).contains("비어");
    assertThat(stagedJson()).isNull();
  }

  @Test
  @DisplayName("given 예약된 사진, when 작업본을 버리면(discard), then 예약도 함께 사라진다")
  void discardClearsStaging() throws Exception {
    long rev = createAndPublish();
    JsonNode up = json(uploadPhoto("textbook1", png(0xabcdef), rev));
    assertThat(stagedJson()).isNotNull();

    MvcResult r = mockMvc.perform(post("/api/tutor/academy/working-copy/discard").cookie(authCookie)
        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + up.get("revision").asLong() + "}")).andReturn();
    authenticate();

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(stagedJson()).isNull();
  }

  @Test
  @DisplayName("given 오래된 revision·잘못된 슬롯·이미지가 아닌 파일, when 올리면, then 409/400이고 예약은 생기지 않는다")
  void uploadGuards() throws Exception {
    long rev = createAndPublish();
    json(uploadPhoto("principal", png(0x111111), rev)); // revision이 올라가 rev는 낡은 값이 된다

    MvcResult stale = uploadPhoto("textbook2", png(0x222222), rev);
    assertThat(stale.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(stale).get("code").asText()).isEqualTo("REVISION_CONFLICT");

    long fresh = state().get("revision").asLong();
    assertThat(uploadPhoto("logo", png(0x222222), fresh).getResponse().getStatus()).isEqualTo(400);
    assertThat(uploadPhoto("textbook2", "not an image".getBytes(), fresh).getResponse().getStatus()).isBetween(400, 499);
    assertThat(stagedJson()).doesNotContain("textbook2");
  }

  @Test
  @DisplayName("given STUDENT·비로그인, when 사진 올리기·미리보기 사진을 호출하면, then 거부된다")
  void nonTutorsAreRejected() throws Exception {
    createAndPublish();
    User student = userRepository.save(new User("st_" + UUID.randomUUID().toString().substring(0, 8), "pw", "학생", "학생", null, null, Role.STUDENT));
    Cookie studentCookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(student.getUserId(), student.getLoginId(), "STUDENT"));
    assertThat(mockMvc.perform(multipart(API + "/media").file(new MockMultipartFile("image", "p.png", "image/png", png(1)))
        .param("slot", "principal").param("revision", "1").cookie(studentCookie)).andReturn().getResponse().getStatus()).isEqualTo(403);
    assertThat(mockMvc.perform(get(API + "/media/principal").cookie(studentCookie)).andReturn().getResponse().getStatus()).isEqualTo(403);
    assertThat(mockMvc.perform(get(API + "/media/principal")).andReturn().getResponse().getStatus()).isIn(401, 403);
  }
}
