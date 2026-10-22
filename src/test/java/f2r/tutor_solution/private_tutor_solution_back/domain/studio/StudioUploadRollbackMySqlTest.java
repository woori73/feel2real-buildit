package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.assertj.core.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/** Actual MySQL trigger fails the 30th cut; storage is the real isolated LocalFileStorage. */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:mysql://127.0.0.1:${F2R_STUDIO_TEST_PORT:3318}/f2r_studio_test",
      "spring.datasource.username=root",
      "spring.datasource.password=isolated_test_root",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.show-sql=false",
      "app.file.upload-dir=/tmp/f2r-studio-rollback-test-files",
      "logging.level.org.hibernate.SQL=WARN"
    })
@EnabledIfEnvironmentVariable(named = "F2R_STUDIO_MYSQL_TEST", matches = "true")
class StudioUploadRollbackMySqlTest {
  @Autowired StudioService studio;
  @Autowired StudioBulkImport bulk;
  @Autowired JdbcTemplate db;
  private final AuthenticatedPrincipal admin = new AuthenticatedPrincipal(5L, "admin", "ADMIN");
  private final Path root = Path.of("/tmp/f2r-studio-rollback-test-files");

  private Set<Path> objects() throws IOException {
    if (!Files.exists(root)) return Set.of();
    try (var files = Files.walk(root)) {
      return files.filter(Files::isRegularFile).collect(java.util.stream.Collectors.toSet());
    }
  }

  private String upload(String name, byte[] bytes) throws IOException {
    return (String)
        ((Map<?, ?>)
                studio
                    .upload(
                        admin,
                        new MockMultipartFile("file", name, "application/octet-stream", bytes))
                    .get("file"))
            .get("id");
  }

  private Map<String, Object> snapshot() {
    var out = new LinkedHashMap<String, Object>();
    for (String table :
        List.of("studio_files", "studio_items", "studio_revisions", "studio_events"))
      out.put(
          table,
          db.queryForList(
              "SELECT * FROM "
                  + table
                  + " ORDER BY "
                  + (table.equals("studio_items") ? "project_id,item_key" : "id")));
    return out;
  }

  private byte[] archive(String tag, byte[] original) throws IOException {
    var manifest = new StringBuilder("{\"episode\":1,\"cuts\":[");
    for (int i = 1; i <= 68; i++) {
      if (i > 1) manifest.append(',');
      manifest
          .append("{\"cutNumber\":")
          .append(i)
          .append(",\"title\":\"rollback test ")
          .append(i)
          .append("\",\"file\":\"asset-")
          .append(i)
          .append(".md\"}");
    }
    manifest.append("]}");
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("manifest.json"));
      zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      for (int i = 1; i <= 68; i++) {
        zip.putNextEntry(new ZipEntry("asset-" + i + ".md"));
        zip.write(
            i == 1 ? original : (tag + " immutable asset " + i).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  @Test
  void
      givenExistingApprovedOriginal_whenCut30DbFailsTwice_thenOnlyNewObjectsRemovedAndRetryDoesNotAccumulate()
          throws Exception {
    studio.initialize(admin);
    String tag = UUID.randomUUID().toString();
    byte[] original = ("existing immutable original " + tag).getBytes(StandardCharsets.UTF_8);
    String originalFile = upload("existing.md", original);
    var revision =
        studio.save(
            admin,
            new StudioService.Revision(
                "rollback-" + tag,
                null,
                "r-original",
                "SCRIPT",
                null,
                null,
                "ORIGINAL",
                "Existing original",
                "preserve history",
                "",
                "",
                "REVIEW",
                originalFile));
    String revisionId = (String) revision.get("id");
    studio.review(admin, revisionId, "APPROVE", "existing approval must survive");
    byte[] zip = archive(tag, original);
    String source = upload("ep01-rollback.zip", zip);
    Set<Path> before = objects();
    var history = snapshot();
    // This is a server-side SQL failure, not a mocked exception before a repository call.
    db.execute(
        "CREATE TRIGGER studio_qa_fail_cut_30 BEFORE INSERT ON studio_revisions FOR EACH ROW BEGIN"
            + " IF NEW.cut_number=30 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic DB"
            + " failure at cut 30'; END IF; END");
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        assertThatThrownBy(() -> bulk.importEp01(admin, source))
            .hasMessageContaining("synthetic DB failure at cut 30");
        assertThat(snapshot())
            .as("all DB rows, original revisions and approval events unchanged")
            .isEqualTo(history);
        assertThat(objects())
            .as("only this attempt's new storage objects were removed")
            .isEqualTo(before);
        assertThat(studio.bytes(admin, originalFile)).isEqualTo(original);
        assertThat(studio.bytes(admin, source)).isEqualTo(zip);
        assertThat(studio.revision(admin, revisionId).get("approved_id")).isEqualTo(revisionId);
      }
    } finally {
      db.execute("DROP TRIGGER studio_qa_fail_cut_30");
    }
    assertThat(((Map<?, ?>) bulk.importEp01(admin, source)).get("registered")).isEqualTo(68);
    assertThat(objects()).hasSize(before.size() + 67); // cut 1 reuses the pre-existing original.
    Set<Path> committed = objects();
    var committedHistory = snapshot();
    assertThatThrownBy(() -> bulk.importEp01(admin, source)).hasMessageContaining("이미 등록한 원본");
    assertThat(objects()).isEqualTo(committed);
    assertThat(snapshot()).isEqualTo(committedHistory);
    assertThat(studio.bytes(admin, originalFile)).isEqualTo(original);
    assertThat(studio.bytes(admin, source)).isEqualTo(zip);
    assertThat(studio.revision(admin, revisionId).get("approved_id")).isEqualTo(revisionId);
  }
}
