package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * BuildIt 통합 화면 1단계 — 작업본(working copy) MySQL 통합 테스트 (실제 HTTP + 실제 V1060 마이그레이션).
 *
 * <p>핵심 불변식: 작업본 수정은 공개 홈페이지를 바꾸지 않고, 게시에서만 공개본이 바뀐다.
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
@DisplayName("작업본 MySQL 통합 테스트 — 게시 전 공개 불변")
class AcademyWorkingCopyMySqlTest {

  private static final String API = "/api/tutor/academy/working-copy";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private AcademyService academyService;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private User tutor;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private Cookie authCookie;
  private String slug;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tutor = userRepository.save(new User("wc_" + suffix, "pw", "작업본강사", "별명", null, null, Role.TUTOR));
    principal = new JwtTokenProvider.AuthenticatedPrincipal(tutor.getUserId(), tutor.getLoginId(), "TUTOR");
    authCookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR"));
    Subject subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    academyService.createDraft(principal,
        new AcademyUpsertRequest("작업본테스트학원", null, "원래 소개문",
            Set.of(subject.getSubjectId()), Set.of(TargetGroup.MIDDLE),
            List.of(new AcademyRegionRequest("서울특별시", "강남구")),
            new AcademyPageSettings(null, null, null, null, null, null, Map.of("designPreset", "ENGLISH"), null)),
        "wc-key-" + suffix);
    em.flush();
    jdbc.update("UPDATE academies SET status = 'PUBLISHED', is_visible = true WHERE tutor_user_id = ?", tutor.getUserId());
    em.clear();
    slug = academyService.getMine(principal).orElseThrow().slug();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ───── 공통 도우미 ─────

  private JsonNode json(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  private MvcResult open() throws Exception {
    return mockMvc.perform(get(API).cookie(authCookie)).andReturn();
  }

  private MvcResult save(long revision, String intro, Map<String, String> builderDraft) throws Exception {
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("revision", revision);
    if (intro != null) body.put("intro", intro);
    if (builderDraft != null) body.put("pageSettings", Map.of("builderDraft", builderDraft));
    return mockMvc.perform(put(API).cookie(authCookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body))).andReturn();
  }

  private MvcResult publish(long revision, boolean force) throws Exception {
    return mockMvc.perform(post(API + "/publish").cookie(authCookie).contentType(MediaType.APPLICATION_JSON)
        .content("{\"revision\":" + revision + ",\"force\":" + force + "}")).andReturn();
  }

  private String liveIntro() {
    em.flush();
    return jdbc.queryForObject("SELECT intro FROM academies WHERE tutor_user_id = ?", String.class, tutor.getUserId());
  }

  private String livePageSettings() {
    em.flush();
    return jdbc.queryForObject("SELECT page_settings FROM academies WHERE tutor_user_id = ?", String.class, tutor.getUserId());
  }

  private int versionCount() {
    em.flush();
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_page_versions v JOIN academies a ON a.academy_id = v.academy_id WHERE a.tutor_user_id = ?",
        Integer.class, tutor.getUserId());
  }

  // ───── 시나리오 ─────

  @Test
  @DisplayName("given 작업본 없음 when 열기 then 공개 내용 복제본이 생기고 공개본은 그대로다")
  void open_createsCopyOfLive_withoutChangingLive() throws Exception {
    String introBefore = liveIntro();
    String settingsBefore = livePageSettings();

    MvcResult r = open();

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(r);
    assertThat(body.get("intro").asText()).isEqualTo("원래 소개문");
    assertThat(body.get("dirty").asBoolean()).isFalse();
    assertThat(body.get("liveChanged").asBoolean()).isFalse();
    assertThat(liveIntro()).isEqualTo(introBefore);
    assertThat(livePageSettings()).isEqualTo(settingsBefore);
  }

  @Test
  @DisplayName("given 작업본 when 수정 then 공개 홈페이지는 변하지 않고 미리보기에만 보인다")
  void save_doesNotChangeLive_butShowsInPreview() throws Exception {
    long rev = json(open()).get("revision").asLong();
    String settingsBefore = livePageSettings();

    MvcResult saved = save(rev, "새 소개문", Map.of("designPreset", "WARM"));

    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(saved).get("dirty").asBoolean()).isTrue();
    assertThat(json(saved).get("revision").asLong()).isGreaterThan(rev);
    // 공개 DB와 공개 API는 그대로
    assertThat(liveIntro()).isEqualTo("원래 소개문");
    assertThat(livePageSettings()).isEqualTo(settingsBefore);
    MvcResult pub = mockMvc.perform(get("/api/academies/" + slug)).andReturn();
    assertThat(json(pub).get("intro").asText()).isEqualTo("원래 소개문");
    assertThat(json(pub).get("pageSettings").get("builderDraft").get("designPreset").asText()).isEqualTo("ENGLISH");
    // 비공개 미리보기에는 작업본 내용이 보이고 캐시·검색 노출은 막힌다
    MvcResult preview = mockMvc.perform(get(API + "/preview").cookie(authCookie)).andReturn();
    assertThat(json(preview).get("intro").asText()).isEqualTo("새 소개문");
    assertThat(json(preview).get("pageSettings").get("builderDraft").get("designPreset").asText()).isEqualTo("WARM");
    assertThat(preview.getResponse().getHeader("Cache-Control")).contains("no-store");
    assertThat(preview.getResponse().getHeader("X-Robots-Tag")).contains("noindex");
  }

  @Test
  @DisplayName("given 수정된 작업본 when 게시 then 공개본이 바뀌고 직전 공개 내용이 버전 기록에 남는다")
  void publish_appliesWorkingCopy_andSnapshotsLive() throws Exception {
    long rev = json(open()).get("revision").asLong();
    long rev2 = json(save(rev, "게시될 소개문", Map.of("designPreset", "WARM"))).get("revision").asLong();
    int versionsBefore = versionCount();

    MvcResult r = publish(rev2, false);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(r).get("dirty").asBoolean()).isFalse();
    assertThat(liveIntro()).isEqualTo("게시될 소개문");
    assertThat(livePageSettings()).contains("WARM");
    assertThat(versionCount()).isEqualTo(versionsBefore + 1);
    // 공개 API도 새 내용
    assertThat(json(mockMvc.perform(get("/api/academies/" + slug)).andReturn()).get("intro").asText())
        .isEqualTo("게시될 소개문");
  }

  @Test
  @DisplayName("given 오래된 revision when 수정 then 409 REVISION_CONFLICT이고 작업본은 그대로")
  void save_staleRevision_conflicts() throws Exception {
    long rev = json(open()).get("revision").asLong();
    save(rev, "첫 수정", null);

    MvcResult stale = save(rev, "늦은 수정", null);

    assertThat(stale.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(stale).get("code").asText()).isEqualTo("REVISION_CONFLICT");
    assertThat(json(open()).get("intro").asText()).isEqualTo("첫 수정");
  }

  @Test
  @DisplayName("given 작업본 생성 후 공개 내용이 다른 곳에서 바뀜 when 게시 then 409 LIVE_CHANGED, force면 반영")
  void publish_liveChangedMeanwhile_conflictsUnlessForced() throws Exception {
    long rev = json(open()).get("revision").asLong();
    long rev2 = json(save(rev, "작업본 소개", null)).get("revision").asLong();
    // 기존 빌더에서 공개본을 직접 수정한 상황 (MockMvc 호출 뒤에는 보안 컨텍스트가 비워지므로 다시 설정)
    authenticate();
    academyService.updateIntro(principal, "빌더에서 직접 바꾼 소개");
    em.flush();

    MvcResult blocked = publish(rev2, false);

    assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(blocked).get("code").asText()).isEqualTo("LIVE_CHANGED");
    assertThat(liveIntro()).isEqualTo("빌더에서 직접 바꾼 소개");
    assertThat(json(open()).get("liveChanged").asBoolean()).isTrue();

    MvcResult forced = publish(rev2, true);

    assertThat(forced.getResponse().getStatus()).isEqualTo(200);
    assertThat(liveIntro()).isEqualTo("작업본 소개");
  }

  @Test
  @DisplayName("given 시각 편집기 게시 상태(custom) when 작업본에서 renderMode 변경 시도 후 게시 then 공개 renderMode는 유지")
  void renderMode_isNeverControlledByWorkingCopy() throws Exception {
    authenticate();
    academyService.setRenderMode(principal, "custom");
    em.flush();
    long rev = json(open()).get("revision").asLong();

    MvcResult saved = save(rev, "소개 수정", Map.of("homepage.renderMode", "template", "designPreset", "ARTS"));
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    long rev2 = json(saved).get("revision").asLong();
    publish(rev2, false);

    JsonNode live = objectMapper.readTree(livePageSettings()).get("builderDraft");
    assertThat(live.get("homepage.renderMode").asText()).isEqualTo("custom");
    assertThat(live.get("designPreset").asText()).isEqualTo("ARTS");
  }

  @Test
  @DisplayName("given 수정된 작업본 when 버리기 then 공개 내용으로 돌아가고 공개본은 불변")
  void discard_resetsToLive() throws Exception {
    long rev = json(open()).get("revision").asLong();
    long rev2 = json(save(rev, "버릴 수정", Map.of("designPreset", "DARK"))).get("revision").asLong();

    MvcResult r = mockMvc.perform(post(API + "/discard").cookie(authCookie)
        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + rev2 + "}")).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(r).get("intro").asText()).isEqualTo("원래 소개문");
    assertThat(json(r).get("dirty").asBoolean()).isFalse();
    assertThat(liveIntro()).isEqualTo("원래 소개문");
  }

  @Test
  @DisplayName("given 게시로 남은 버전 when 복원 then 작업본만 그 내용이 되고 공개본은 불변")
  void restore_loadsVersionIntoWorkingCopyOnly() throws Exception {
    long rev = json(open()).get("revision").asLong();
    long rev2 = json(save(rev, "두 번째 소개", Map.of("designPreset", "WARM"))).get("revision").asLong();
    long rev3 = json(publish(rev2, false)).get("revision").asLong();
    Long versionId = jdbc.queryForObject(
        "SELECT v.id FROM academy_page_versions v JOIN academies a ON a.academy_id = v.academy_id "
            + "WHERE a.tutor_user_id = ? ORDER BY v.version_no DESC LIMIT 1", Long.class, tutor.getUserId());

    MvcResult r = mockMvc.perform(post(API + "/restore").cookie(authCookie)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"revision\":" + rev3 + ",\"versionId\":" + versionId + "}")).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(r).get("intro").asText()).isEqualTo("원래 소개문");   // 게시 전 공개 내용
    assertThat(json(r).get("pageSettings").get("builderDraft").get("designPreset").asText()).isEqualTo("ENGLISH");
    assertThat(liveIntro()).isEqualTo("두 번째 소개");                    // 공개본은 그대로
  }

  @Test
  @DisplayName("given 로그인 없음 when 작업본 접근 then 거부")
  void unauthenticated_isRejected() throws Exception {
    int status = mockMvc.perform(get(API)).andReturn().getResponse().getStatus();
    assertThat(status).isIn(401, 403);
  }
}
