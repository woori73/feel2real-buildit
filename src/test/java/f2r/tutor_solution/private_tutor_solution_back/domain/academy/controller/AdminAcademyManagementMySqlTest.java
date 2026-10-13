package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 타인 홈페이지 관리(PR-B) MySQL 통합 테스트 — 실제 보안 필터 체인과 V1062.
 * 핵심: 관리자 수정은 관리자 전용 작업본에만, 공개본은 게시 전 불변, 회원 데이터와 분리, 감사 기록, 보호 장치.
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
@DisplayName("관리자 홈페이지 관리 MySQL 통합 테스트")
class AdminAcademyManagementMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private AcademyService academyService;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private User admin;
  private User owner;
  private User otherOwner;
  private User student;
  private Cookie adminCookie;
  private Cookie ownerCookie;
  private Cookie studentCookie;
  private AcademyResponse target;
  private AcademyResponse other;

  @BeforeEach
  void setUp() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    admin = userRepository.save(new User("am_" + s, "pw", "관리자", "관리", null, null, Role.ADMIN));
    owner = userRepository.save(new User("ow_" + s, "pw", "원장", "원장", null, null, Role.TUTOR));
    otherOwner = userRepository.save(new User("ot_" + s, "pw", "다른원장", "다른", null, null, Role.TUTOR));
    student = userRepository.save(new User("sd_" + s, "pw", "학생", "학생", null, null, Role.STUDENT));
    adminCookie = cookie(admin, "ADMIN");
    ownerCookie = cookie(owner, "TUTOR");
    studentCookie = cookie(student, "STUDENT");
    target = createPublishedAcademy(owner, "TUTOR", "대상학원", "원래 소개문");
    other = createPublishedAcademy(otherOwner, "TUTOR", "다른학원", "다른 소개문");
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private Cookie cookie(User u, String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(u.getUserId(), u.getLoginId(), role));
  }

  private AcademyResponse createPublishedAcademy(User tutor, String role, String name, String intro) {
    Subject subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(tutor.getUserId(), tutor.getLoginId(), role), null, List.of()));
    AcademyResponse r = academyService.createDraft(
        new JwtTokenProvider.AuthenticatedPrincipal(tutor.getUserId(), tutor.getLoginId(), role),
        new AcademyUpsertRequest(name, null, intro, Set.of(subject.getSubjectId()), Set.of(TargetGroup.MIDDLE),
            List.of(new AcademyRegionRequest("서울특별시", "강남구")),
            new AcademyPageSettings(null, null, null, null, null, null, Map.of("designPreset", "ENGLISH"), null)),
        "k-" + UUID.randomUUID());
    em.flush();
    jdbc.update("UPDATE academies SET status = 'PUBLISHED', is_visible = true WHERE academy_id = ?", r.academyId());
    em.clear();
    return r;
  }

  private String api(AcademyResponse a, String suffix) {
    return "/api/admin/academies/" + a.academyId() + "/builder" + suffix;
  }

  private MvcResult call(RequestBuilder rb) throws Exception {
    return mockMvc.perform(rb).andReturn();
  }

  private JsonNode json(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  private MvcResult adminPut(AcademyResponse a, Map<String, Object> body) throws Exception {
    return call(put(api(a, "")).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)));
  }

  private MvcResult adminPost(AcademyResponse a, String suffix, Map<String, Object> body) throws Exception {
    return call(post(api(a, suffix)).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)));
  }

  private long adminOpenRevision(AcademyResponse a) throws Exception {
    return json(call(get(api(a, "")).cookie(adminCookie))).get("revision").asLong();
  }

  private String live(AcademyResponse a, String column) {
    em.flush();
    return jdbc.queryForObject("SELECT " + column + " FROM academies WHERE academy_id = ?", String.class, a.academyId());
  }

  private int count(String sql, Object... args) {
    em.flush();
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private Map<String, Object> publishBody(AcademyResponse a, long revision) {
    return Map.of("revision", revision, "confirmAcademyId", a.academyId(), "confirmHandle", a.handle());
  }

  @Test
  @DisplayName("given ADMIN, when 대상 학원을 열면, then 학원 요약이 오고 접근 감사 기록이 남으며 공개본은 그대로다")
  void openRecordsAccessAndLeavesLiveUntouched() throws Exception {
    String introBefore = live(target, "intro");
    MvcResult r = call(get(api(target, "")).cookie(adminCookie));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(r);
    assertThat(body.get("academy").get("academyId").asLong()).isEqualTo(target.academyId());
    assertThat(body.get("academy").get("ownerLoginId").asText()).isEqualTo(owner.getLoginId());
    assertThat(count("SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='ACCESS' AND actor_user_id=?",
        target.academyId(), admin.getUserId())).isEqualTo(1);
    assertThat(live(target, "intro")).isEqualTo(introBefore);
  }

  @Test
  @DisplayName("given 관리자 수정, when 저장하면, then 공개본·회원 작업본·회원 대화는 그대로이고 감사 기록에 전/후가 남는다")
  void saveIsPrivateToAdminWorkingCopyAndAudited() throws Exception {
    String introBefore = live(target, "intro");
    String settingsBefore = live(target, "page_settings");
    long rev = adminOpenRevision(target);

    MvcResult r = adminPut(target, Map.of("revision", rev, "intro", "관리자가 고친 소개",
        "pageSettings", Map.of("builderDraft", Map.of("introHeading", "관리자 제목"))));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);

    // 공개본 불변
    assertThat(live(target, "intro")).isEqualTo(introBefore);
    assertThat(live(target, "page_settings")).isEqualTo(settingsBefore);
    // 회원 작업본은 만들어지지 않았거나 관리자 내용을 담지 않는다
    assertThat(count("SELECT COUNT(*) FROM academy_working_copy WHERE academy_id=? AND intro LIKE '%관리자가 고친%'",
        target.academyId())).isZero();
    // 회원 대화에는 아무것도 쓰이지 않는다
    assertThat(count("SELECT COUNT(*) FROM academy_assistant_message WHERE academy_id=?", target.academyId())).isZero();
    // 회원 상태 API에는 관리자 내용이 없다
    MvcResult ownerState = call(get("/api/tutor/academy/assistant/state").cookie(ownerCookie));
    assertThat(ownerState.getResponse().getContentAsString()).doesNotContain("관리자가 고친").doesNotContain("관리자 제목");
    // 감사 기록: 작업자·대상·전/후
    em.flush();
    Map<String, Object> audit = jdbc.queryForMap(
        "SELECT actor_login_id, owner_user_id, action, published, details_json FROM academy_admin_audit "
            + "WHERE academy_id=? AND action='SAVE'", target.academyId());
    assertThat(audit.get("actor_login_id")).isEqualTo(admin.getLoginId());
    assertThat(((Number) audit.get("owner_user_id")).longValue()).isEqualTo(owner.getUserId());
    assertThat((Boolean) audit.get("published")).isFalse();
    assertThat((String) audit.get("details_json"))
        .contains("introHeading").contains("관리자 제목").contains("관리자가 고친 소개").contains("원래 소개문");
  }

  @Test
  @DisplayName("given 관리자 수정, when 관리자 미리보기를 보면 수정이 보이고, 공개 API는 게시 전까지 그대로다")
  void previewShowsAdminWorkButPublicApiDoesNot() throws Exception {
    long rev = adminOpenRevision(target);
    adminPut(target, Map.of("revision", rev, "intro", "미리보기용 소개"));
    JsonNode preview = json(call(get(api(target, "/preview")).cookie(adminCookie)));
    assertThat(preview.get("intro").asText()).isEqualTo("미리보기용 소개");
    JsonNode publicApi = json(call(get("/api/academies/" + target.slug())));
    assertThat(publicApi.get("intro").asText()).isEqualTo("원래 소개문");
  }

  @Test
  @DisplayName("given 낡은 revision, when 저장하면, then 409 REVISION_CONFLICT이고 아무것도 바뀌지 않는다")
  void staleRevisionRejected() throws Exception {
    long rev = adminOpenRevision(target);
    assertThat(adminPut(target, Map.of("revision", rev + 5, "intro", "x")).getResponse().getStatus()).isEqualTo(409);
    assertThat(count("SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='SAVE'", target.academyId()))
        .isZero();
  }

  @Test
  @DisplayName("given 잘못된 확인 값, when 게시하면, then 409 TARGET_MISMATCH이고 공개본·버전·감사 기록이 그대로다")
  void publishRequiresMatchingTargetConfirmation() throws Exception {
    long rev = adminOpenRevision(target);
    adminPut(target, Map.of("revision", rev, "intro", "게시될 소개"));
    long rev2 = adminOpenRevision(target);

    // 다른 학원 ID로 확인
    MvcResult wrongId = adminPost(target, "/publish",
        Map.of("revision", rev2, "confirmAcademyId", other.academyId(), "confirmHandle", target.handle()));
    assertThat(wrongId.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(wrongId).get("code").asText()).isEqualTo("TARGET_MISMATCH");
    // 핸들 불일치
    MvcResult wrongHandle = adminPost(target, "/publish",
        Map.of("revision", rev2, "confirmAcademyId", target.academyId(), "confirmHandle", "wrong-handle"));
    assertThat(json(wrongHandle).get("code").asText()).isEqualTo("TARGET_MISMATCH");
    // 확인 값 없음
    assertThat(adminPost(target, "/publish", Map.of("revision", rev2)).getResponse().getStatus()).isEqualTo(409);

    assertThat(live(target, "intro")).isEqualTo("원래 소개문");
    assertThat(count("SELECT COUNT(*) FROM academy_page_versions WHERE academy_id=?", target.academyId())).isZero();
    assertThat(count("SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='PUBLISH'", target.academyId()))
        .isZero();
  }

  @Test
  @DisplayName("given 올바른 확인, when 게시하면, then 공개본이 바뀌고 직전 공개본이 버전에 남으며 감사 기록이 published로 남는다")
  void publishAppliesAndKeepsPreviousVersion() throws Exception {
    long rev = adminOpenRevision(target);
    adminPut(target, Map.of("revision", rev, "intro", "게시될 소개"));
    long rev2 = adminOpenRevision(target);

    MvcResult r = adminPost(target, "/publish", publishBody(target, rev2));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(r).get("firstPublish").asBoolean()).isFalse();

    assertThat(live(target, "intro")).isEqualTo("게시될 소개");
    // 직전 공개본이 버전으로 보관되고 작업자가 남는다
    em.flush();
    Map<String, Object> version = jdbc.queryForMap(
        "SELECT intro, created_by, note FROM academy_page_versions WHERE academy_id=?", target.academyId());
    assertThat(version.get("intro")).isEqualTo("원래 소개문");
    assertThat(version.get("created_by")).isEqualTo(String.valueOf(admin.getUserId()));
    assertThat((String) version.get("note")).contains(admin.getLoginId());
    assertThat(count("SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='PUBLISH' AND published=1",
        target.academyId())).isEqualTo(1);
    // 공개 API에도 반영
    assertThat(json(call(get("/api/academies/" + target.slug()))).get("intro").asText()).isEqualTo("게시될 소개");
  }

  @Test
  @DisplayName("given DRAFT 학원, when 확인 없이 게시하면 409, 최초 공개 확인과 함께 게시하면 PUBLISHED가 된다")
  void firstPublishNeedsExplicitConfirmation() throws Exception {
    jdbc.update("UPDATE academies SET status='DRAFT' WHERE academy_id=?", target.academyId());
    em.clear();
    long rev = adminOpenRevision(target);
    adminPut(target, Map.of("revision", rev, "intro", "최초 공개 소개"));
    long rev2 = adminOpenRevision(target);

    MvcResult denied = adminPost(target, "/publish", publishBody(target, rev2));
    assertThat(denied.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(denied).get("code").asText()).isEqualTo("FIRST_PUBLISH_CONFIRM_REQUIRED");
    assertThat(live(target, "status")).isEqualTo("DRAFT");

    Map<String, Object> body = new java.util.HashMap<>(publishBody(target, rev2));
    body.put("confirmFirstPublish", true);
    MvcResult ok = adminPost(target, "/publish", body);
    assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(ok).get("firstPublish").asBoolean()).isTrue();
    assertThat(live(target, "status")).isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("given 게시 이력, when 버전을 복원하면, then 작업본에만 불러오고 공개본은 불변이며, 다른 학원 버전은 403이다")
  void restoreWorksOnAdminWorkingCopyAndIsScopedToAcademy() throws Exception {
    long rev = adminOpenRevision(target);
    adminPut(target, Map.of("revision", rev, "intro", "두번째 소개"));
    adminPost(target, "/publish", publishBody(target, adminOpenRevision(target)));

    JsonNode versions = json(call(get(api(target, "/versions")).cookie(adminCookie)));
    assertThat(versions).hasSize(1);
    long versionId = versions.get(0).get("id").asLong();

    MvcResult restored = adminPost(target, "/restore", Map.of("revision", adminOpenRevision(target), "versionId", versionId));
    assertThat(restored.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(restored).get("intro").asText()).isEqualTo("원래 소개문");
    assertThat(live(target, "intro")).isEqualTo("두번째 소개"); // 공개본 불변
    assertThat(count("SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='RESTORE'", target.academyId()))
        .isEqualTo(1);

    // 다른 학원 기준으로 이 버전을 복원하려 하면 거부
    long otherRev = adminOpenRevision(other);
    assertThat(adminPost(other, "/restore", Map.of("revision", otherRev, "versionId", versionId))
        .getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("given 관리자 작업본에 수정, when 되돌리기하면, then 공개 내용으로 돌아가고 기록이 남는다")
  void discardResetsToLive() throws Exception {
    long rev = adminOpenRevision(target);
    adminPut(target, Map.of("revision", rev, "intro", "버릴 소개"));
    MvcResult r = adminPost(target, "/discard", Map.of("revision", adminOpenRevision(target)));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(r).get("intro").asText()).isEqualTo("원래 소개문");
    assertThat(count("SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=? AND action='DISCARD'", target.academyId()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("given 두 학원, when 관리자가 대상 하나를 수정하면, then 다른 학원은 전혀 바뀌지 않는다 (잘못된 학원 보호)")
  void otherAcademyIsUntouched() throws Exception {
    long rev = adminOpenRevision(target);
    adminPut(target, Map.of("revision", rev, "intro", "대상만"));
    adminPost(target, "/publish", publishBody(target, adminOpenRevision(target)));
    assertThat(live(other, "intro")).isEqualTo("다른 소개문");
    assertThat(count("SELECT COUNT(*) FROM academy_admin_working_copy WHERE academy_id=?", other.academyId())).isZero();
    assertThat(count("SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=?", other.academyId())).isZero();
  }

  @Test
  @DisplayName("given 감사 기록, when ADMIN이 조회하면 보이고, 회원 API에는 노출되지 않는다")
  void auditVisibleToAdminOnly() throws Exception {
    adminOpenRevision(target);
    JsonNode rows = json(call(get(api(target, "/audit")).cookie(adminCookie)));
    assertThat(rows).isNotEmpty();
    assertThat(rows.get(0).get("action").asText()).isEqualTo("ACCESS");
    assertThat(call(get(api(target, "/audit")).cookie(ownerCookie)).getResponse().getStatus()).isEqualTo(403);
    assertThat(call(get("/api/tutor/academy/assistant/state").cookie(ownerCookie)).getResponse().getContentAsString())
        .doesNotContain("audit").doesNotContain(admin.getLoginId());
  }

  @Test
  @DisplayName("given TUTOR/STUDENT/비로그인, when 관리자 API·미리보기 페이지에 접근하면, then 모두 거부된다")
  void nonAdminsAreDenied() throws Exception {
    for (Cookie c : new Cookie[] {ownerCookie, studentCookie}) {
      assertThat(call(get(api(target, "")).cookie(c)).getResponse().getStatus()).isEqualTo(403);
      assertThat(call(put(api(target, "")).cookie(c).contentType(MediaType.APPLICATION_JSON)
          .content("{\"revision\":0,\"intro\":\"x\"}")).getResponse().getStatus()).isEqualTo(403);
      assertThat(call(post(api(target, "/publish")).cookie(c).contentType(MediaType.APPLICATION_JSON)
          .content("{\"revision\":0}")).getResponse().getStatus()).isEqualTo(403);
      assertThat(call(get("/admin/academies/" + target.academyId() + "/preview").cookie(c)).getResponse().getStatus())
          .isEqualTo(403);
      assertThat(call(get("/admin/academies/" + target.academyId() + "/builder").cookie(c)).getResponse().getStatus())
          .isEqualTo(403);
    }
    assertThat(call(get("/admin/academies/" + target.academyId() + "/builder")).getResponse().getStatus()).isIn(401, 403);
    assertThat(call(get(api(target, ""))).getResponse().getStatus()).isIn(401, 403);
    assertThat(call(get("/admin/academies/" + target.academyId() + "/preview")).getResponse().getStatus()).isIn(401, 403);
    // 다른 쪽도 변하지 않았다
    assertThat(count("SELECT COUNT(*) FROM academy_admin_audit WHERE academy_id=?", target.academyId())).isZero();
  }

  @Test
  @DisplayName("given ADMIN, when 관리자 미리보기 페이지를 열면 200, 없는 학원이면 404")
  void adminPreviewPage() throws Exception {
    MvcResult page = call(get("/admin/academies/" + target.academyId() + "/preview").cookie(adminCookie));
    assertThat(page.getResponse().getStatus()).isEqualTo(200);
    assertThat(page.getResponse().getContentAsString()).contains("data-preview-api=\"/api/admin/academies/" + target.academyId() + "/builder/preview\"");
    assertThat(call(get("/admin/academies/999999999/preview").cookie(adminCookie)).getResponse().getStatus()).isEqualTo(404);
    MvcResult manage = call(get("/admin/academies/" + target.academyId() + "/builder").cookie(adminCookie));
    assertThat(manage.getResponse().getStatus()).isEqualTo(200);
    assertThat(manage.getResponse().getContentAsString()).contains("data-academy-id=\"" + target.academyId() + "\"");
    assertThat(manage.getResponse().getHeader("Cache-Control")).contains("no-store");
    assertThat(call(get("/admin/academies/999999999/builder").cookie(adminCookie)).getResponse().getStatus()).isEqualTo(404);
    assertThat(call(get("/api/admin/academies/999999999/builder").cookie(adminCookie)).getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  @DisplayName("given 회원 본인의 기존 API, when TUTOR가 자기 작업본을 열면, then 기존처럼 동작하고 관리자 작업과 섞이지 않는다")
  void ownerWorkingCopyUnaffected() throws Exception {
    long rev = adminOpenRevision(target);
    adminPut(target, Map.of("revision", rev, "intro", "관리자 전용"));
    MvcResult mine = call(get("/api/tutor/academy/working-copy").cookie(ownerCookie));
    assertThat(mine.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(mine).get("intro").asText()).isEqualTo("원래 소개문");
  }
}
