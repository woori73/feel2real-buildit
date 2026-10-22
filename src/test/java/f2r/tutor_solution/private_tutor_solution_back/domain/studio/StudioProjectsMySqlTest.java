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
class StudioProjectsMySqlTest {
  @Autowired StudioService studio;
  @Autowired StudioProjectService projects;
  @Autowired StudioCollaborationService collaboration;
  @Autowired StudioProductionImporter importer;
  @Autowired JdbcTemplate db;
  @Autowired UserRepository users;
  @Autowired ObjectMapper json;
  AuthenticatedPrincipal admin, editor, viewer, stranger;
  String a, b, c;

  AuthenticatedPrincipal user(Role role) {
    String id = "projects-" + UUID.randomUUID().toString().substring(0, 10);
    var u =
        users.saveAndFlush(
            User.builder()
                .loginId(id)
                .password("{isolated-only}")
                .name("격리 프로젝트 팀원")
                .nickname(id)
                .role(role)
                .build());
    return new AuthenticatedPrincipal(u.getUserId(), u.getLoginId(), role.name(), "isolated");
  }

  void select(String id) {
    var request = new MockHttpServletRequest();
    request.setParameter("projectId", id);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @BeforeEach
  void setup() {
    admin = user(Role.ADMIN);
    editor = user(Role.TUTOR);
    viewer = user(Role.STUDENT);
    stranger = user(Role.TUTOR);
    a = json.valueToTree(projects.create(admin, "격리 프로젝트 A")).path("id").asText();
    b = json.valueToTree(projects.create(admin, "격리 프로젝트 B")).path("id").asText();
    c = json.valueToTree(projects.create(admin, "격리 프로젝트 C")).path("id").asText();
    select(a);
    studio.member(admin, editor.userId(), "EDITOR");
    studio.member(admin, viewer.userId(), "VIEWER");
  }

  @AfterEach
  void reset() {
    RequestContextHolder.resetRequestAttributes();
  }

  StudioService.Revision draft(String file) {
    return new StudioService.Revision(
        "shared-key",
        null,
        "r1",
        "SCRIPT",
        null,
        null,
        "USER_EDIT",
        "격리 문서",
        "실제 테스트 문서",
        "",
        "",
        "REVIEW",
        file);
  }

  StudioCollaborationService.Memory memory(String file) {
    return new StudioCollaborationService.Memory(
        null,
        "격리 결정",
        "Codex",
        "미확보 유지",
        "검수",
        867,
        "a".repeat(40),
        file == null ? List.of() : List.of(file));
  }

  String upload() throws Exception {
    return (String)
        ((Map<?, ?>)
                studio
                    .upload(
                        editor,
                        new MockMultipartFile(
                            "file",
                            "isolated.md",
                            "text/markdown",
                            "# 격리 프로젝트 근거".getBytes(StandardCharsets.UTF_8)))
                    .get("file"))
            .get("id");
  }

  @Test
  void givenThreeProjects_whenSelected_thenListsAndAllResourcesRemainIsolated() throws Exception {
    String file = upload(), revision = (String) studio.save(editor, draft(file)).get("id");
    collaboration.saveMemory(editor, memory(file));
    collaboration.comment(
        editor, revision, new StudioCollaborationService.Comment("프로젝트 A 의견", null, null));
    assertEquals(1, json.valueToTree(projects.projects(editor)).size());
    select(b);
    studio.member(admin, editor.userId(), "EDITOR");
    assertTrue(studio.files(editor, 200, 0).isEmpty());
    assertTrue(studio.revisions(editor, null, null, null, 200, 0).isEmpty());
    assertFalse(json.valueToTree(collaboration.memory(editor)).path("memory").has("lastDecision"));
    assertThrows(RuntimeException.class, () -> studio.file(editor, file));
    assertThrows(RuntimeException.class, () -> studio.revision(editor, revision));
    assertThrows(RuntimeException.class, () -> collaboration.comments(editor, revision, null));
    assertThrows(RuntimeException.class, () -> collaboration.saveMemory(editor, memory(file)));
    assertThrows(RuntimeException.class, () -> studio.save(editor, draft(file)));
    studio.save(editor, draft(null));
    assertEquals(2, json.valueToTree(projects.projects(editor)).size());
    select(c);
    assertThrows(RuntimeException.class, () -> studio.revisions(editor, null, null, null, 200, 0));
    assertThrows(RuntimeException.class, () -> studio.overview(stranger));
    var visible = new HashSet<String>();
    json.valueToTree(projects.projects(admin)).forEach(row -> visible.add(row.path("id").asText()));
    assertTrue(visible.containsAll(List.of(a, b, c)));
    select(a);
    assertEquals(
        revision, studio.revisions(editor, null, "SCRIPT", null, 200, 0).getFirst().get("id"));
    assertEquals(file, studio.file(editor, file).get("id"));
  }

  @Test
  void givenApprovedOriginalAndMemory_whenArchivedRenamedAndReopened_thenAllEvidencePreserved()
      throws Exception {
    String file = upload(), id = (String) studio.save(editor, draft(file)).get("id");
    studio.review(admin, id, "APPROVE", "원본 승인");
    var memory = collaboration.saveMemory(editor, memory(file));
    byte[] bytes = studio.bytes(editor, file);
    var before = studio.revision(editor, id);
    long
        files =
            db.queryForObject(
                "SELECT COUNT(*) FROM studio_files WHERE project_id=?", Long.class, a),
        revisions =
            db.queryForObject(
                "SELECT COUNT(*) FROM studio_revisions WHERE project_id=?", Long.class, a);
    projects.change(admin, "ARCHIVE", null);
    assertTrue(studio.archived());
    assertEquals(1, json.valueToTree(projects.projects(viewer)).get(0).path("archived").asInt());
    assertThrows(RuntimeException.class, () -> studio.save(editor, draft(null)));
    assertThrows(RuntimeException.class, () -> studio.review(admin, id, "REJECT", "거부"));
    assertThrows(
        RuntimeException.class,
        () ->
            collaboration.comment(
                editor, id, new StudioCollaborationService.Comment("수정", null, null)));
    assertThrows(
        RuntimeException.class,
        () ->
            studio.upload(
                editor, new MockMultipartFile("file", "new.md", "text/markdown", new byte[] {65})));
    projects.change(admin, "TITLE", "새 프로젝트 제목");
    assertEquals("새 프로젝트 제목", studio.overview(viewer).get("title"));
    assertEquals(before, studio.revision(viewer, id));
    assertArrayEquals(bytes, studio.bytes(viewer, file));
    assertEquals(memory, collaboration.memory(viewer));
    assertEquals(
        files,
        db.queryForObject("SELECT COUNT(*) FROM studio_files WHERE project_id=?", Long.class, a));
    assertEquals(
        revisions,
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_revisions WHERE project_id=?", Long.class, a));
    assertThrows(RuntimeException.class, () -> projects.change(admin, "DELETE", null));
    projects.change(admin, "REOPEN", null);
    assertFalse(studio.archived());
    collaboration.comment(
        editor, id, new StudioCollaborationService.Comment("재개 후 의견", null, null));
    var actions =
        db.queryForList("SELECT action FROM studio_events WHERE project_id=?", String.class, a);
    assertTrue(
        actions.containsAll(
            List.of(
                "PROJECT_CREATE",
                "PROJECT_ARCHIVE",
                "PROJECT_TITLE",
                "PROJECT_REOPEN",
                "APPROVE")));
    assertEquals(id, studio.revision(viewer, id).get("approved_id"));
  }

  @Test
  void givenInvitation_whenAcceptedOnlyByRecipient_thenPendingNeverGrantsAccess() {
    projects.invite(admin, stranger.userId(), "EDITOR");
    assertThrows(RuntimeException.class, () -> studio.overview(stranger));
    assertEquals(0, json.valueToTree(projects.projects(stranger)).size());
    assertEquals(1, json.valueToTree(projects.invitations(stranger)).size());
    assertThrows(RuntimeException.class, () -> projects.answerInvitation(viewer, true));
    projects.answerInvitation(stranger, true);
    assertEquals("EDITOR", studio.permission(stranger));
    assertEquals(0, json.valueToTree(projects.invitations(stranger)).size());
    assertThrows(RuntimeException.class, () -> projects.answerInvitation(stranger, true));
    studio.member(admin, stranger.userId(), "REVOKE");
    assertThrows(RuntimeException.class, () -> studio.overview(stranger));
  }

  @Test
  void givenArchivedProject_whenInvitingOrAccepting_thenRefusedUntilReopened() {
    projects.invite(admin, stranger.userId(), "VIEWER");
    projects.change(admin, "ARCHIVE", null);
    assertThrows(RuntimeException.class, () -> projects.answerInvitation(stranger, true));
    assertThrows(RuntimeException.class, () -> projects.invite(admin, viewer.userId(), "EDITOR"));
    projects.answerInvitation(stranger, false);
    assertEquals(0, json.valueToTree(projects.invitations(stranger)).size());
    projects.change(admin, "REOPEN", null);
    projects.invite(admin, stranger.userId(), "VIEWER");
    projects.answerInvitation(stranger, true);
    assertThrows(RuntimeException.class, () -> studio.save(stranger, draft(null)));
  }

  @Test
  void givenNotifications_whenReadOrAcknowledged_thenNoGetWritesAndOwnProjectCursorOnly() {
    String id = (String) studio.save(editor, draft(null)).get("id");
    studio.review(admin, id, "REQUEST_CHANGES", "확인 요청");
    long before = db.queryForObject("SELECT COUNT(*) FROM studio_events", Long.class);
    var notes = json.valueToTree(projects.notifications(viewer, null));
    assertEquals(before, db.queryForObject("SELECT COUNT(*) FROM studio_events", Long.class));
    long event = notes.path("items").get(0).path("id").asLong();
    assertEquals(0, notes.path("readThrough").asLong());
    projects.markNotifications(viewer, event);
    assertEquals(
        event, json.valueToTree(projects.notifications(viewer, null)).path("readThrough").asLong());
    assertEquals(
        0, json.valueToTree(projects.notifications(editor, null)).path("readThrough").asLong());
    select(b);
    studio.member(admin, viewer.userId(), "VIEWER");
    assertThrows(RuntimeException.class, () -> projects.markNotifications(viewer, event));
    assertTrue(json.valueToTree(projects.notifications(viewer, null)).path("items").isEmpty());
  }

  @Test
  void givenUnauthorizedOrInvalidLifecycle_whenCalled_thenRejectedWithoutWrites() {
    long before = db.queryForObject("SELECT COUNT(*) FROM studio_projects", Long.class);
    assertThrows(RuntimeException.class, () -> projects.create(editor, "우회"));
    assertThrows(RuntimeException.class, () -> projects.create(admin, " "));
    assertThrows(RuntimeException.class, () -> projects.change(editor, "ARCHIVE", null));
    assertThrows(
        RuntimeException.class, () -> projects.invite(editor, stranger.userId(), "APPROVER"));
    assertThrows(RuntimeException.class, () -> projects.searchUsers(viewer, "격리"));
    assertThrows(RuntimeException.class, () -> projects.invite(admin, stranger.userId(), "OWNER"));
    assertEquals(before, db.queryForObject("SELECT COUNT(*) FROM studio_projects", Long.class));
    var users = json.valueToTree(projects.searchUsers(admin, "격리 프로젝트"));
    assertTrue(users.size() > 0);
    assertFalse(users.get(0).has("email"));
    assertFalse(users.get(0).has("password"));
    assertEquals(0, json.valueToTree(projects.searchUsers(admin, "%%")).size());
  }

  @Test
  void givenOtherProject_whenReadingPinnedBundle_thenDefaultAssetsNeverExposedOrImported()
      throws Exception {
    select(b);
    var status = json.valueToTree(importer.status(admin));
    assertFalse(status.path("sourceImportAvailable").asBoolean());
    assertEquals(0, status.path("registeredSourcePaths").asInt());
    assertFalse(status.has("handoffDocuments"));
    assertThrows(
        RuntimeException.class,
        () ->
            importer.register(
                admin,
                new StudioProductionImporter.Request(StudioProductionImporter.COMMIT, Map.of())));
  }

  @Test
  void givenAmbiguousOrTraversalProjectId_whenResolved_thenFailClosed() {
    var request = new MockHttpServletRequest();
    request.setParameter("projectId", new String[] {a, b});
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    assertThrows(RuntimeException.class, () -> studio.projectId());
    select("../" + a);
    assertThrows(RuntimeException.class, () -> studio.projectId());
    RequestContextHolder.resetRequestAttributes();
    assertEquals(StudioService.PROJECT, studio.projectId());
  }
}
