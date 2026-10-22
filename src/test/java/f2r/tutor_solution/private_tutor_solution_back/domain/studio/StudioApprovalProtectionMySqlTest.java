package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3318/f2r_studio_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci",
      "spring.datasource.username=f2r_test",
      "spring.datasource.password=isolated_test_password",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.show-sql=false",
      "app.file.upload-dir=/tmp/f2r-studio-test-files"
    })
@EnabledIfEnvironmentVariable(named = "F2R_STUDIO_MYSQL_TEST", matches = "true")
@Transactional
class StudioApprovalProtectionMySqlTest {
  @Autowired StudioService studio;
  @Autowired StudioCollaborationService collaboration;
  @Autowired StudioProjectService projects;
  @Autowired JdbcTemplate db;
  @Autowired UserRepository users;
  @Autowired ObjectMapper json;
  AuthenticatedPrincipal admin, editor, viewer, stranger;
  String project, key, id, file;

  AuthenticatedPrincipal user(Role role) {
    String login = "approval-" + UUID.randomUUID().toString().substring(0, 10);
    var u =
        users.saveAndFlush(
            User.builder()
                .loginId(login)
                .password("{isolated-only}")
                .name("격리 승인 보호")
                .nickname(login)
                .role(role)
                .build());
    return new AuthenticatedPrincipal(u.getUserId(), login, role.name(), "isolated");
  }

  void select(String project) {
    var request = new MockHttpServletRequest();
    request.setParameter("projectId", project);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @BeforeEach
  void setup() throws Exception {
    admin = user(Role.ADMIN);
    editor = user(Role.TUTOR);
    viewer = user(Role.STUDENT);
    stranger = user(Role.TUTOR);
    project = json.valueToTree(projects.create(admin, "격리 승인 보호")).path("id").asText();
    select(project);
    studio.member(admin, editor.userId(), "EDITOR");
    studio.member(admin, viewer.userId(), "VIEWER");
    key = "approval-" + UUID.randomUUID();
    file =
        (String)
            ((Map<?, ?>)
                    studio
                        .upload(
                            editor,
                            new MockMultipartFile(
                                "file",
                                "approval.md",
                                "text/markdown",
                                "# 승인 원본 불변 검증".getBytes(StandardCharsets.UTF_8)))
                        .get("file"))
                .get("id");
    id = (String) studio.save(editor, revision(null, "r1")).get("id");
    studio.review(admin, id, "APPROVE", "초기 승인 근거");
  }

  @AfterEach
  void reset() {
    RequestContextHolder.resetRequestAttributes();
  }

  StudioService.Revision revision(String parent, String label) {
    return new StudioService.Revision(
        key,
        parent,
        label,
        "STORYBOARD",
        1,
        1,
        "USER_EDIT",
        "격리 승인 컷",
        "검증 설명",
        "대사",
        "연출",
        "REVIEW",
        file);
  }

  void status(int code, org.junit.jupiter.api.function.Executable f) {
    assertEquals(code, assertThrows(ResponseStatusException.class, f).getStatusCode().value());
  }

  long events() {
    return db.queryForObject(
        "SELECT COUNT(*) FROM studio_events WHERE project_id=?", Long.class, project);
  }

  Map<String, Object> raw() {
    return db.queryForMap("SELECT * FROM studio_revisions WHERE id=?", id);
  }

  @Test
  void givenApprovedRevision_whenOrdinaryReject_then409AndOriginalAndApprovalUntouched()
      throws Exception {
    var original = raw();
    var metadata = studio.file(viewer, file);
    var bytes = studio.bytes(viewer, file);
    long count = events();
    status(409, () -> studio.review(admin, id, "REJECT", "일반 반려"));
    assertEquals(id, studio.revision(viewer, id).get("approved_id"));
    assertEquals(original, raw());
    assertEquals(metadata, studio.file(viewer, file));
    assertArrayEquals(bytes, studio.bytes(viewer, file));
    assertEquals(count, events());
  }

  @Test
  void givenUnapprovedCandidate_whenRejected_thenExistingApprovedOriginalRemains() {
    String candidate = (String) studio.save(editor, revision(id, "r2")).get("id");
    studio.review(admin, candidate, "REJECT", "후보 반려");
    assertEquals(id, studio.revision(viewer, candidate).get("approved_id"));
    assertTrue(
        json.valueToTree(collaboration.comments(viewer, candidate, null))
            .toString()
            .contains("후보 반려"));
  }

  @Test
  void givenMissingIntentOrOldReviewEndpoint_whenWithdrawing_thenNoBypass() {
    long before = events();
    status(400, () -> studio.withdrawApproval(admin, id, false, id, "사유"));
    status(400, () -> studio.withdrawApproval(admin, id, true, null, "사유"));
    status(400, () -> studio.withdrawApproval(admin, id, true, id, " "));
    status(400, () -> studio.review(admin, id, "WITHDRAW_APPROVAL", "우회"));
    assertEquals(id, studio.revision(viewer, id).get("approved_id"));
    assertEquals(before, events());
  }

  @Test
  void givenHumanConfirmedWithdrawal_whenSaved_thenRereadAuditAndAllOriginalEvidenceRemain()
      throws Exception {
    var original = raw();
    byte[] bytes = studio.bytes(viewer, file);
    var metadata = studio.file(viewer, file);
    long before = events();
    var saved = studio.withdrawApproval(admin, id, true, id, "사용자가 확인한 승인 해제");
    assertNull(saved.get("approved_id"));
    assertNull(studio.revision(viewer, id).get("approved_id"));
    assertEquals(original, raw());
    assertEquals(metadata, studio.file(viewer, file));
    assertArrayEquals(bytes, studio.bytes(viewer, file));
    assertEquals(before + 1, events());
    var event =
        db.queryForMap(
            "SELECT * FROM studio_events WHERE project_id=? AND action='WITHDRAW_APPROVAL'",
            project);
    assertEquals(id, event.get("revision_id"));
    assertEquals(admin.userId(), ((Number) event.get("actor_id")).longValue());
    assertEquals("사용자가 확인한 승인 해제", event.get("note"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_events WHERE project_id=? AND revision_id=? AND"
                + " action='APPROVE'",
            Integer.class,
            project,
            id));
    assertTrue(json.valueToTree(collaboration.pending(viewer, 100, 0)).toString().contains(id));
    assertTrue(
        json.valueToTree(collaboration.comments(viewer, id, null))
            .toString()
            .contains("WITHDRAW_APPROVAL"));
    assertTrue(
        json.valueToTree(projects.notifications(viewer, null))
            .toString()
            .contains("WITHDRAW_APPROVAL"));
    assertEquals(before + 1, events());
    status(409, () -> studio.withdrawApproval(admin, id, true, id, "중복"));
    assertEquals(before + 1, events());
  }

  @Test
  void givenApprovalMovedToAnotherRevision_whenOldDialogConfirmed_then409() {
    String candidate = (String) studio.save(editor, revision(id, "r2")).get("id");
    studio.review(admin, candidate, "APPROVE", "새 후보 승인");
    long before = events();
    status(409, () -> studio.withdrawApproval(admin, id, true, id, "오래된 화면"));
    assertEquals(candidate, studio.revision(viewer, id).get("approved_id"));
    assertEquals(before, events());
  }

  @Test
  void givenWrongRoleOrProject_whenWithdrawing_thenAccessDenied() {
    long before = events();
    for (var p : List.of(editor, viewer, stranger))
      status(403, () -> studio.withdrawApproval(p, id, true, id, "무권한"));
    assertEquals(before, events());
    String foreign = json.valueToTree(projects.create(admin, "격리 외부 작품")).path("id").asText();
    select(foreign);
    status(404, () -> studio.withdrawApproval(admin, id, true, id, "외부 자료"));
    select(project);
    assertEquals(id, studio.revision(viewer, id).get("approved_id"));
  }

  @Test
  void givenArchivedProject_whenWithdrawing_thenResumeRequired() {
    projects.change(admin, "ARCHIVE", null);
    status(409, () -> studio.withdrawApproval(admin, id, true, id, "보관 중"));
    assertEquals(id, studio.revision(viewer, id).get("approved_id"));
    projects.change(admin, "REOPEN", null);
    assertNull(studio.withdrawApproval(admin, id, true, id, "재개 후 명시적 확인").get("approved_id"));
  }
}
