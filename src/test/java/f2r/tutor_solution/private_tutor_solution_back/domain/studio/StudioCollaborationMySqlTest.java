package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
class StudioCollaborationMySqlTest {
  @Autowired StudioService studio;
  @Autowired StudioCollaborationService collaboration;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  AuthenticatedPrincipal admin, editor, viewer, stranger;

  AuthenticatedPrincipal user(Role role) {
    String id = "memory-" + UUID.randomUUID().toString().substring(0, 12);
    var u =
        users.saveAndFlush(
            User.builder()
                .loginId(id)
                .password("{isolated-only}")
                .name("격리 공동 기억")
                .nickname(id)
                .role(role)
                .build());
    return new AuthenticatedPrincipal(u.getUserId(), u.getLoginId(), role.name(), "isolated");
  }

  @BeforeEach
  void setup() {
    admin = user(Role.ADMIN);
    editor = user(Role.TUTOR);
    viewer = user(Role.STUDENT);
    stranger = user(Role.TUTOR);
    studio.initialize(admin);
    studio.member(admin, editor.userId(), "EDITOR");
    studio.member(admin, viewer.userId(), "VIEWER");
  }

  StudioService.Revision revision(String key, String parent, String label) {
    return new StudioService.Revision(
        key,
        parent,
        label,
        "STORYBOARD",
        1,
        1,
        "USER_EDIT",
        "격리 댓글 컷",
        "내용",
        "대사",
        "연출",
        "REVIEW",
        null);
  }

  StudioCollaborationService.Memory memory(String expected, List<String> files, String decision) {
    return new StudioCollaborationService.Memory(
        expected, decision, "Codex", "Claude 검수 대기", "다음 작업 확인", 866, "a".repeat(40), files);
  }

  @Test
  void givenSharedMemory_whenSavedAndReread_thenNewVersionAndStaleEditorConflict()
      throws Exception {
    String file =
        (String)
            ((Map<?, ?>)
                    studio
                        .upload(
                            editor,
                            new MockMultipartFile(
                                "file",
                                "memory.md",
                                "text/markdown",
                                "# 공동 근거".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .get("file"))
                .get("id");
    var saved =
        (Map<?, ?>) collaboration.saveMemory(editor, memory(null, List.of(file), "마지막 결정 보존"));
    String id = (String) saved.get("revisionId");
    var restored = (Map<?, ?>) collaboration.memory(viewer);
    assertEquals(id, restored.get("revisionId"));
    assertEquals(
        "마지막 결정 보존", json.valueToTree(restored.get("memory")).path("lastDecision").asText());
    var next = (Map<?, ?>) collaboration.saveMemory(editor, memory(id, List.of(file), "다음 결정"));
    assertNotEquals(id, next.get("revisionId"));
    assertThrows(
        RuntimeException.class,
        () -> collaboration.saveMemory(editor, memory(id, List.of(file), "충돌")));
    assertEquals(
        "마지막 결정 보존",
        json.readTree((String) studio.revision(editor, id).get("content"))
            .path("lastDecision")
            .asText());
  }

  @Test
  void givenViewerOrStranger_whenWritingMemoryOrComments_thenRejected() {
    var r = studio.save(editor, revision("comment-" + UUID.randomUUID(), null, "r1"));
    String id = (String) r.get("id");
    assertThrows(
        RuntimeException.class,
        () -> collaboration.saveMemory(viewer, memory(null, List.of(), "권한 없음")));
    assertThrows(
        RuntimeException.class,
        () ->
            collaboration.comment(
                viewer, id, new StudioCollaborationService.Comment("읽기만", null, null)));
    assertThrows(RuntimeException.class, () -> collaboration.memory(stranger));
    assertThrows(RuntimeException.class, () -> collaboration.comments(stranger, id, null));
  }

  @Test
  void givenOldApprovedRevision_whenCommentedAndChangesRequested_thenOriginalAndThreadPreserved()
      throws Exception {
    String key = "thread-" + UUID.randomUUID();
    var first = studio.save(editor, revision(key, null, "r1"));
    String one = (String) first.get("id");
    studio.review(admin, one, "APPROVE", "원본 승인");
    var second = studio.save(editor, revision(key, one, "r2"));
    String two = (String) second.get("id");
    collaboration.comment(
        editor, one, new StudioCollaborationService.Comment("이전 컷 의견", null, null));
    studio.review(admin, two, "REQUEST_CHANGES", "수정 필요");
    assertEquals(one, studio.revision(editor, two).get("approved_id"));
    studio.review(admin, one, "REQUEST_CHANGES", "승인 정본 유지하며 의견");
    assertEquals(one, studio.revision(editor, two).get("approved_id"));
    var comments = json.valueToTree(collaboration.comments(viewer, two, null));
    assertTrue(comments.toString().contains("이전 컷 의견"));
    assertTrue(comments.toString().contains("r1"));
    assertTrue(comments.toString().contains("REQUEST_CHANGES"));
  }

  @Test
  void givenForeignFileOrUnprovenAiComment_whenLinked_thenRejectedWithoutMemoryWrite()
      throws Exception {
    String project = "foreign-" + UUID.randomUUID(), id = UUID.randomUUID().toString();
    db.update("INSERT INTO studio_projects(id,title) VALUES (?,?)", project, "격리 외부 프로젝트");
    db.update(
        "INSERT INTO"
            + " studio_files(id,project_id,sha256,storage_key,filename,extension,size_bytes,uploaded_by)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        id,
        project,
        "f".repeat(64),
        "not-read",
        "foreign.md",
        "md",
        1,
        editor.userId());
    assertThrows(
        RuntimeException.class,
        () -> collaboration.saveMemory(editor, memory(null, List.of(id), "외부 파일")));
    var r = studio.save(editor, revision("proof-" + UUID.randomUUID(), null, "r1"));
    assertThrows(
        RuntimeException.class,
        () ->
            collaboration.comment(
                editor,
                (String) r.get("id"),
                new StudioCollaborationService.Comment("AI 의견", "Claude", null)));
  }

  @Test
  void givenHumanSharedAiMaterial_whenCommentRecorded_thenActualHumanActorAndEvidenceRemain()
      throws Exception {
    String file =
        (String)
            ((Map<?, ?>)
                    studio
                        .upload(
                            editor,
                            new MockMultipartFile(
                                "file",
                                "claude-review.md",
                                "text/markdown",
                                "# 검수 자료".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .get("file"))
                .get("id");
    var r = studio.save(editor, revision("ai-note-" + UUID.randomUUID(), null, "r1"));
    collaboration.comment(
        editor,
        (String) r.get("id"),
        new StudioCollaborationService.Comment("검수 자료를 공유합니다.", "Claude", file));
    var comments = json.valueToTree(collaboration.comments(viewer, (String) r.get("id"), null));
    var note = json.readTree(comments.get(0).path("note").asText());
    assertEquals("HUMAN_SHARED_AI_MATERIAL", note.path("authorType").asText());
    assertEquals(editor.userId(), comments.get(0).path("actor_id").asLong());
    assertEquals(file, note.path("sourceFileId").asText());
  }

  @Test
  void given205PendingItems_whenPaged_thenNoneLostTo200RowLimit() {
    var ids = new HashSet<String>();
    String prefix = "inbox-" + UUID.randomUUID().toString().substring(0, 8);
    for (int i = 0; i < 205; i++)
      ids.add(
          (String)
              studio
                  .save(
                      editor,
                      new StudioService.Revision(
                          prefix + "-" + i,
                          null,
                          "r1",
                          "SCRIPT",
                          null,
                          null,
                          "USER_EDIT",
                          "검수 대기 " + i,
                          "",
                          "",
                          "",
                          "REVIEW",
                          null))
                  .get("id"));
    var seen = new HashSet<String>();
    int offset = 0;
    List<?> rows;
    do {
      rows = (List<?>) collaboration.pending(viewer, 100, offset);
      for (var row : rows) seen.add((String) ((Map<?, ?>) row).get("id"));
      offset += rows.size();
    } while (rows.size() == 100);
    assertTrue(seen.containsAll(ids));
    assertThrows(RuntimeException.class, () -> collaboration.pending(viewer, 101, 0));
  }
}
