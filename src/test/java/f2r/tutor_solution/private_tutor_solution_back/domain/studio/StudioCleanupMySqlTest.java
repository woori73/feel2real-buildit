package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 MySQL에서 폐지 자료 2단계 정리(보관 → 14일 뒤 영구 삭제)와 자동 차단을 검증한다.
 *
 * <p>영구 삭제의 저장소 파일 삭제는 커밋 뒤에 실행되므로 테스트 클래스에 트랜잭션을 걸지 않고, 테스트마다 격리 프로젝트를 새로 만든다.
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3318/f2r_studio_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci",
      "spring.datasource.username=f2r_test",
      "spring.datasource.password=isolated_test_password",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.show-sql=false",
      "app.file.upload-dir=/tmp/f2r-studio-cleanup-test-files"
    })
@EnabledIfEnvironmentVariable(named = "F2R_STUDIO_MYSQL_TEST", matches = "true")
class StudioCleanupMySqlTest {
  @Autowired StudioService studio;
  @Autowired StudioCleanupService cleanup;
  @Autowired StudioCatalog catalog;
  @Autowired StudioProjectService projects;
  @Autowired JdbcTemplate db;
  @Autowired UserRepository users;
  @Autowired FileStorage storage;
  @Autowired ObjectMapper json;
  AuthenticatedPrincipal admin, editor;
  String project, key, fileA, fileB, r1, r2;

  AuthenticatedPrincipal user(Role role) {
    String login = "cleanup-" + UUID.randomUUID().toString().substring(0, 10);
    var u =
        users.saveAndFlush(
            User.builder()
                .loginId(login)
                .password("{isolated-only}")
                .name("격리 정리 검증")
                .nickname(login)
                .role(role)
                .build());
    return new AuthenticatedPrincipal(u.getUserId(), login, role.name(), "isolated");
  }

  void select(String id) {
    var request = new MockHttpServletRequest();
    request.setParameter("projectId", id);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  String upload(String text) throws Exception {
    var file =
        new MockMultipartFile(
            "file", "cleanup.md", "text/markdown", text.getBytes(StandardCharsets.UTF_8));
    return (String) ((Map<?, ?>) studio.upload(editor, file).get("file")).get("id");
  }

  String save(String itemKey, String parent, String label, String content, String fileId) {
    return (String)
        studio
            .save(
                editor,
                new StudioService.Revision(
                    itemKey, parent, label, "STORYBOARD", 1, 5, "AI_PROPOSAL", "정리 검증 컷",
                    content, "", "", "REVIEW", fileId))
            .get("id");
  }

  void needsChanges(String revisionId) {
    db.update(
        "INSERT INTO studio_reviews(id,project_id,revision_id,item_key,verdict,summary,issues_json,"
            + "author_type,user_id,request_sha256) VALUES (?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(), project, revisionId, key, "NEEDS_CHANGES", "눈선 반대", "[]",
        "AI_CONNECTOR", editor.userId(), "0".repeat(64));
  }

  void ageArchive(String revisionId, int days) {
    db.update(
        "UPDATE studio_revisions SET archived_at=CURRENT_TIMESTAMP - INTERVAL ? DAY WHERE id=?",
        days, revisionId);
  }

  @SuppressWarnings("unchecked")
  List<String> ids(Map<String, Object> result, String field) {
    var out = new ArrayList<String>();
    for (Object o : (List<Object>) result.get(field))
      out.add(o instanceof Map<?, ?> m ? (String) m.get("revisionId") : (String) o);
    return out;
  }

  @SuppressWarnings("unchecked")
  String skipReason(Map<String, Object> result) {
    return (String) ((List<Map<String, Object>>) result.get("skipped")).getFirst().get("reason");
  }

  long count(String sql, Object... args) {
    return db.queryForObject(sql, Long.class, args);
  }

  @BeforeEach
  void setup() throws Exception {
    admin = user(Role.ADMIN);
    editor = user(Role.TUTOR);
    project = json.valueToTree(projects.create(admin, "격리 폐지 정리")).path("id").asText();
    select(project);
    studio.member(admin, editor.userId(), "EDITOR");
    key = "EP01_V6_GPT_S11R11_05_end_" + UUID.randomUUID().toString().substring(0, 8);
    fileA = upload("# 앞선 버전 " + UUID.randomUUID());
    fileB = upload("# 최신 버전 " + UUID.randomUUID());
    r1 = save(key, null, "r1", "방향 반대", fileA);
    r2 = save(key, r1, "r2", "방향 수정", fileB);
  }

  @AfterEach
  void reset() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void givenOlderVersionWithNeedsChanges_whenCandidatesListed_thenProposedWithFreeableBytes() {
    // given
    needsChanges(r1);
    // when
    var result = cleanup.candidates(admin);
    // then
    assertEquals(List.of(r1), ids(result, "candidates"));
    long size = count("SELECT size_bytes FROM studio_files WHERE id=?", fileA);
    assertEquals(size, ((Number) result.get("freeableBytes")).longValue());
  }

  @Test
  void givenHeadVersion_whenArchived_thenSkippedAndStillVisible() {
    // when
    var result = cleanup.archive(admin, List.of(r2), "감독 확인 테스트");
    // then
    assertTrue(ids(result, "archived").isEmpty());
    assertEquals("최신본(head)", skipReason(result));
    assertEquals(0, count("SELECT COUNT(*) FROM studio_revisions WHERE id=? AND archived_at IS NOT NULL", r2));
  }

  @Test
  void givenApprovedOlderVersion_whenArchived_thenSkippedAsApproved() {
    // given
    studio.review(admin, r1, "APPROVE", "승인본 보호 검증");
    // when
    var result = cleanup.archive(admin, List.of(r1), "감독 확인 테스트");
    // then
    assertEquals("승인본", skipReason(result));
  }

  @Test
  void givenVersionReferencedByDocument_whenArchived_thenBlocked() {
    // given — 다른 자료 본문이 이 버전 ID를 적어 두었다(인수인계 문서 등).
    save("EP01_V6_NOTE_" + UUID.randomUUID().toString().substring(0, 8), null, "r1", "참조: " + r1, null);
    // when
    var result = cleanup.archive(admin, List.of(r1), "감독 확인 테스트");
    // then
    assertEquals("다른 자료가 이 버전을 참조함", skipReason(result));
  }

  @Test
  void givenVersionReferencedByTask_whenArchived_thenBlocked() {
    // given
    db.update(
        "INSERT INTO studio_tasks(id,project_id,item_key,revision_id,title,instructions,assignee,status,"
            + "author_type,created_by,request_sha256) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(), project, key, r1, "다시 그리기", "방향 수정", "AI", "DONE", "USER",
        admin.userId(), "0".repeat(64));
    // when
    var result = cleanup.archive(admin, List.of(r1), "감독 확인 테스트");
    // then
    assertEquals("수정 작업이 참조함", skipReason(result));
  }

  @Test
  void givenNonAdmin_whenArchiveRequested_thenForbidden() {
    // when
    var e =
        assertThrows(
            ResponseStatusException.class, () -> cleanup.archive(editor, List.of(r1), "메모"));
    // then
    assertEquals(403, e.getStatusCode().value());
    assertEquals(0, count("SELECT COUNT(*) FROM studio_revisions WHERE id=? AND archived_at IS NOT NULL", r1));
  }

  @Test
  void givenOlderVersion_whenArchivedThenRestored_thenHiddenThenVisibleAndLogged() {
    // given
    needsChanges(r1);
    // when
    var archived = cleanup.archive(admin, List.of(r1), "1차 감독 확인");
    // then — 목록·검색에서 빠지고 보관함에 있다.
    assertEquals(List.of(r1), ids(archived, "archived"));
    assertTrue(studio.revisions(admin, null, null, null).stream().noneMatch(r -> r1.equals(r.get("id"))));
    assertTrue(
        catalog.list(admin, "materials", "", "", "", "", "all", "", 100, 0).stream()
            .noneMatch(r -> r1.equals(r.get("id"))));
    assertEquals(List.of(r1), cleanup.archived(admin).stream().map(r -> (String) r.get("revisionId")).toList());
    // when
    var restored = cleanup.restore(admin, List.of(r1), "되살림 확인");
    // then
    assertEquals(List.of(r1), ids(restored, "restored"));
    assertTrue(studio.revisions(admin, null, null, null).stream().anyMatch(r -> r1.equals(r.get("id"))));
    assertEquals(
        List.of("RESTORE", "ARCHIVE"),
        cleanup.log(admin).stream().map(r -> (String) r.get("action")).toList());
  }

  @Test
  void givenArchivedVersion_whenApproved_thenConflict() {
    // given
    cleanup.archive(admin, List.of(r1), "감독 확인 테스트");
    // when
    var e =
        assertThrows(ResponseStatusException.class, () -> studio.review(admin, r1, "APPROVE", "승인 시도"));
    // then
    assertEquals(409, e.getStatusCode().value());
  }

  @Test
  void givenArchivedLessThan14Days_whenPurged_thenSkippedAndKept() {
    // given
    cleanup.archive(admin, List.of(r1), "감독 확인 테스트");
    ageArchive(r1, 13);
    // when
    var result = cleanup.purge(admin, List.of(r1), "영구 삭제 시도");
    // then
    assertTrue(ids(result, "purged").isEmpty());
    assertTrue(skipReason(result).contains("14일"));
    assertEquals(1, count("SELECT COUNT(*) FROM studio_revisions WHERE id=?", r1));
  }

  @Test
  void givenNotArchived_whenPurged_thenSkipped() {
    // when
    var result = cleanup.purge(admin, List.of(r1), "영구 삭제 시도");
    // then
    assertTrue(skipReason(result).contains("먼저 보관"));
    assertEquals(1, count("SELECT COUNT(*) FROM studio_revisions WHERE id=?", r1));
  }

  @Test
  void givenArchived15DaysAgo_whenPurged_thenRowAndUnsharedFileDeletedAndChainRelinked() {
    // given
    needsChanges(r1);
    cleanup.archive(admin, List.of(r1), "감독 확인 테스트");
    ageArchive(r1, 15);
    String storageKey = db.queryForObject("SELECT storage_key FROM studio_files WHERE id=?", String.class, fileA);
    long size = count("SELECT size_bytes FROM studio_files WHERE id=?", fileA);
    // when
    var result = cleanup.purge(admin, List.of(r1), "11/26 감독 확인 목록");
    // then
    assertEquals(List.of(r1), ids(result, "purged"));
    assertEquals(size, ((Number) result.get("bytesFreed")).longValue());
    assertEquals(0, count("SELECT COUNT(*) FROM studio_revisions WHERE id=?", r1));
    assertEquals(0, count("SELECT COUNT(*) FROM studio_reviews WHERE revision_id=?", r1));
    assertEquals(0, count("SELECT COUNT(*) FROM studio_files WHERE id=?", fileA));
    assertThrows(Exception.class, () -> storage.readSingleLimited(storageKey, "UNKNOWN", 1024));
    assertNull(db.queryForObject("SELECT parent_id FROM studio_revisions WHERE id=?", String.class, r2));
    var log = cleanup.log(admin).getFirst();
    assertEquals("PURGE", log.get("action"));
    assertEquals(size, ((Number) log.get("bytes_freed")).longValue());
    assertEquals(admin.userId(), ((Number) log.get("actor_id")).longValue());
  }

  @Test
  void givenFileSharedByAnotherVersion_whenPurged_thenRowDeletedButFileKept() {
    // given — 다른 작업 ID가 같은 파일을 쓴다.
    save("EP01_V6_OTHER_" + UUID.randomUUID().toString().substring(0, 8), null, "r1", "같은 파일", fileA);
    cleanup.archive(admin, List.of(r1), "감독 확인 테스트");
    ageArchive(r1, 15);
    // when
    var result = cleanup.purge(admin, List.of(r1), "감독 확인 목록");
    // then
    assertEquals(List.of(r1), ids(result, "purged"));
    assertEquals(0L, ((Number) result.get("bytesFreed")).longValue());
    assertEquals(1, count("SELECT COUNT(*) FROM studio_files WHERE id=?", fileA));
  }

  @Test
  void givenFileIdMentionedInDocument_whenPurged_thenFileKept() {
    // given — 인수인계 문서처럼 본문이 fileId를 적어 두었다.
    save("EP01_V6_NOTE_" + UUID.randomUUID().toString().substring(0, 8), null, "r1", "원본 파일 " + fileA, null);
    cleanup.archive(admin, List.of(r1), "감독 확인 테스트");
    ageArchive(r1, 15);
    // when
    var result = cleanup.purge(admin, List.of(r1), "감독 확인 목록");
    // then
    assertEquals(List.of(r1), ids(result, "purged"));
    assertEquals(1, count("SELECT COUNT(*) FROM studio_files WHERE id=?", fileA));
  }
}
