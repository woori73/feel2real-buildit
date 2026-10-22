package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/** Pinned real #863 files into isolated local DB/storage; consumer apps are NOT TESTED. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("local")
@TestPropertySource(
    properties = {
      "server.port=19089",
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3319/f2r_cluster_test",
      "spring.datasource.username=root",
      "spring.datasource.password=isolated_cluster_root",
      "spring.flyway.locations=classpath:db/migration,classpath:db/dev-seed",
      "spring.jpa.show-sql=false",
      "logging.level.org.hibernate.SQL=WARN",
      "app.file.storage-type=local",
      "app.file.upload-dir=/tmp/f2r-production-review-files",
      "app.collaboration.public-base-url=http://127.0.0.1:19089",
      "app.collaboration.production-import.enabled=true",
      "app.mcp.enabled=false"
    })
@EnabledIfEnvironmentVariable(named = "F2R_COLLAB_PRODUCTION_TEST", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollaborationProductionMySqlTest {
  @Autowired CollaborationProductionImport importer;
  @Autowired CollaborationWorkspaces spaces;
  @Autowired CollaborationSources sources;
  @Autowired CollaborationFiles files;
  @Autowired CollaborationPreview preview;
  @Autowired StudioService studio;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate db;
  @Autowired JwtTokenProvider jwt;
  AuthenticatedPrincipal owner;
  String workspace, batch, reviewWorkspace, reviewedCut, reviewedReference;
  Path source;
  final Path evidence = Path.of("docs/collaboration/production-results");
  final com.fasterxml.jackson.databind.ObjectMapper json =
      new com.fasterxml.jackson.databind.ObjectMapper();
  CollaborationProductionImport.Plan plan;

  void write(String name, Object value) throws Exception {
    Files.createDirectories(evidence);
    Files.writeString(
        evidence.resolve(name),
        json.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
  }

  @BeforeAll
  void setup() {
    source =
        Path.of(
            Objects.requireNonNull(
                System.getenv("F2R_COLLAB_PRODUCTION_SOURCE"), "Materialize pinned source first"));
    String login = "production-" + UUID.randomUUID().toString().substring(0, 12);
    var user =
        users.saveAndFlush(
            new User(login, "isolated-test-password", "실자료 격리 검증", login, null, null, Role.ADMIN));
    owner = new AuthenticatedPrincipal(user.getUserId(), login, "ADMIN");
    studio.initialize(owner);
    workspace = spaces.create(owner, "《AI AGENT》 EP01 · #863 실제 제작 자료").get("id").toString();
  }

  @Test
  @Order(1)
  void dryRunAndAtomic536FileImportPreserveOriginalsAndZeroCanonicalPass() throws Exception {
    var pointers =
        db.queryForList("SELECT item_key,head_id,approved_id FROM studio_items ORDER BY item_key");
    long before = db.queryForObject("SELECT COUNT(*) FROM ai_collaboration_files", Long.class);
    plan = importer.dryRun(source);
    assertEquals(536, plan.files());
    assertEquals(532, plan.materials());
    assertEquals(4, plan.handoffDocs());
    assertEquals(316, plan.uniqueSha());
    assertEquals(220, plan.duplicateSha());
    assertEquals(12, plan.checksumMetadata());
    assertEquals(5, plan.approvedReferences());
    assertEquals(68, plan.roughPanels());
    assertEquals(164559341, plan.bytes());
    assertEquals(
        before, db.queryForObject("SELECT COUNT(*) FROM ai_collaboration_files", Long.class));
    write("dry-run.json", plan);
    var result = importer.apply(owner, workspace, source);
    batch = result.get("importId").toString();
    assertEquals(536, result.get("registered"));
    assertEquals(536, result.get("shaVerified"));
    assertEquals(
        536,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_files WHERE workspace_id=? AND status='DRAFT'",
            Integer.class,
            workspace));
    assertEquals(
        68,
        sources.production(owner, workspace).get("cuts") instanceof List<?> cuts ? cuts.size() : 0);
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_storyboard_cuts WHERE import_id=? AND"
                + " pass_confirmed=TRUE",
            Integer.class,
            batch));
    assertEquals(
        5,
        db.queryForObject(
            "SELECT COUNT(DISTINCT f.sha256) FROM ai_collaboration_sources s JOIN"
                + " ai_collaboration_files f ON f.id=s.file_id WHERE s.import_id=? AND"
                + " s.source_approval='APPROVED_REFERENCE'",
            Integer.class,
            batch));
    assertEquals(
        12,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_sources WHERE import_id=? AND"
                + " category='CHECKSUM_METADATA'",
            Integer.class,
            batch));
    assertEquals(
        pointers,
        db.queryForList("SELECT item_key,head_id,approved_id FROM studio_items ORDER BY item_key"));
    var report = new LinkedHashMap<String, Object>(result);
    report.put("workspaceId", workspace);
    report.put("allNewFilesDraft", true);
    report.put("existingApprovedPointersUnchanged", true);
    report.put("productionDatabaseChanged", false);
    report.put("actualChatGptClaudeConnection", "NOT TESTED");
    write("import-results.json", report);
    var rows =
        db.queryForList(
            "SELECT"
                + " s.source_path,s.version_label,s.category,s.source_approval,f.sha256,f.size_bytes,f.status"
                + " FROM ai_collaboration_sources s JOIN ai_collaboration_files f ON f.id=s.file_id"
                + " WHERE s.import_id=? ORDER BY s.source_path",
            batch);
    write(
        "sha-comparison.json",
        Map.of("sourceRef", plan.sourceRef(), "compared", 536, "mismatches", 0, "files", rows));
  }

  @Test
  @Order(2)
  void replayHasNoNewFilesNoNewHistoryAndAll536SourcesPaginate() throws Exception {
    long events =
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_workspace_events WHERE workspace_id=?",
            Long.class,
            workspace);
    var repeated = importer.apply(owner, workspace, source);
    assertEquals(true, repeated.get("idempotentReplay"));
    assertEquals(batch, repeated.get("importId"));
    assertEquals(
        events,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_workspace_events WHERE workspace_id=?",
            Long.class,
            workspace));
    var ids = new HashSet<String>();
    String cursor = "";
    int pages = 0;
    do {
      var page = sources.files(owner, workspace, cursor, 100, "");
      for (var row : (List<Map<String, Object>>) page.get("items"))
        assertTrue(ids.add(row.get("id").toString()));
      cursor = page.get("nextBeforeId").toString();
      pages++;
    } while (!cursor.isEmpty());
    assertEquals(536, ids.size());
    assertEquals(6, pages);
    var memory = spaces.snapshot(owner, workspace).get("memory").toString();
    for (var name : List.of("CURRENT_STATE.md", "APPROVAL_HISTORY.md", "MISSING_FILES.md"))
      assertTrue(memory.contains(name));
    write(
        "replay-pagination.json",
        Map.of(
            "registeredAfterReplay",
            536,
            "newFilesOnReplay",
            0,
            "newEventsOnReplay",
            0,
            "pages",
            pages,
            "uniqueFilesRead",
            ids.size(),
            "handoffMemory",
            List.of("CURRENT_STATE.md", "APPROVAL_HISTORY.md", "MISSING_FILES.md")));
  }

  long storedFiles() throws Exception {
    if (!Files.exists(Path.of("/tmp/f2r-production-review-files"))) return 0;
    try (var all = Files.walk(Path.of("/tmp/f2r-production-review-files"))) {
      return all.filter(Files::isRegularFile).count();
    }
  }

  @Test
  @Order(3)
  void actualSqlFailureRollsBackBatchAndNewBlobsWhileKeepingExistingApproval() throws Exception {
    String failed = spaces.create(owner, "임포트 롤백 격리 검증").get("id").toString();
    var ref =
        plan.entries().stream()
            .filter(e -> e.sourceApproval().equals("APPROVED_REFERENCE"))
            .findFirst()
            .orElseThrow();
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(
            Objects.requireNonNull(
                (org.springframework.transaction.PlatformTransactionManager)
                    context.getBean(
                        org.springframework.transaction.PlatformTransactionManager.class)));
    var protectedFile =
        transaction.execute(
            t -> {
              try {
                return files.store(
                    owner,
                    failed,
                    Path.of(ref.path()).getFileName().toString(),
                    Files.readAllBytes(source.resolve(ref.path())),
                    null,
                    "CODEX",
                    null);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    String protectedId = protectedFile.get("id").toString();
    files.review(owner, protectedId, "APPROVED");
    long storedBefore = storedFiles();
    db.execute(
        "CREATE TRIGGER collab_import_failure BEFORE INSERT ON ai_collaboration_sources FOR EACH"
            + " ROW BEGIN IF (SELECT workspace_id FROM ai_collaboration_imports WHERE"
            + " id=NEW.import_id)='"
            + failed
            + "' AND NEW.source_path='MISSING_FILES.md' THEN SIGNAL SQLSTATE '45000' SET"
            + " MESSAGE_TEXT='isolated_import_failure'; END IF; END");
    try {
      assertThrows(RuntimeException.class, () -> importer.apply(owner, failed, source));
    } finally {
      db.execute("DROP TRIGGER collab_import_failure");
    }
    assertEquals(storedBefore, storedFiles());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_imports WHERE workspace_id=?",
            Integer.class,
            failed));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_files WHERE workspace_id=?",
            Integer.class,
            failed));
    assertEquals("APPROVED", files.metadata(owner, protectedId).get("status"));
    assertEquals(ref.sha256(), CollaborationFiles.sha(files.bytes(owner, protectedId)));
    var recovered = importer.apply(owner, failed, source);
    assertEquals(536, recovered.get("registered"));
    assertEquals("APPROVED", files.metadata(owner, protectedId).get("status"));
    reviewWorkspace = failed;
    reviewedCut =
        db.queryForObject(
            "SELECT panel_file_id FROM ai_collaboration_storyboard_cuts WHERE import_id=? AND"
                + " cut_number=1",
            String.class,
            recovered.get("importId"));
    reviewedReference =
        db.queryForObject(
            "SELECT s.file_id FROM ai_collaboration_sources s JOIN ai_collaboration_files f ON"
                + " f.id=s.file_id WHERE f.workspace_id=? AND"
                + " s.source_approval='APPROVED_REFERENCE' ORDER BY s.source_path LIMIT 1",
            String.class,
            failed);
    files.review(owner, reviewedCut, "APPROVED");
    files.review(owner, reviewedReference, "APPROVED");
    importer.apply(owner, failed, source);
    assertEquals("APPROVED", files.metadata(owner, reviewedCut).get("status"));
    assertEquals("APPROVED", files.metadata(owner, reviewedReference).get("status"));
    var reviewedCuts = (List<Map<String, Object>>) sources.production(owner, failed).get("cuts");
    assertEquals("APPROVED", reviewedCuts.getFirst().get("status"));
    assertEquals(0, sources.production(owner, failed).get("passConfirmed"));
    assertEquals(
        536,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_files WHERE workspace_id=? AND status='DRAFT'",
            Integer.class,
            workspace));
    write(
        "human-approval-display.json",
        Map.of(
            "separateIsolatedReviewWorkspace",
            true,
            "humanReviewPath",
            "CollaborationFiles.review",
            "reviewedPanelAndReference",
            2,
            "approvalPreservedOnReplay",
            true,
            "productionImportWorkspaceDraftsUnchanged",
            536,
            "canonicalPass",
            0));
    write(
        "rollback.json",
        Map.of(
            "injectedActualMySqlFailure",
            true,
            "partialBatchRows",
            0,
            "orphanNewBlobs",
            0,
            "retryRegisteredFiles",
            536,
            "existingApprovedBytesUnchanged",
            true,
            "existingApprovedStatusUnchanged",
            true));
  }

  @Autowired org.springframework.context.ApplicationContext context;

  @Test
  @Order(4)
  void disabledImporterFailsClosedAndPreviewsCannotExecuteHtmlOrChangeOriginals() throws Exception {
    ReflectionTestUtils.setField(importer, "enabled", false);
    try {
      assertThrows(
          org.springframework.web.server.ResponseStatusException.class,
          () -> importer.dryRun(source));
    } finally {
      ReflectionTestUtils.setField(importer, "enabled", true);
    }
    var html =
        plan.entries().stream().filter(e -> e.path().endsWith(".html")).findFirst().orElseThrow();
    String id =
        db.queryForObject(
            "SELECT file_id FROM ai_collaboration_sources WHERE import_id=? AND source_path=?",
            String.class,
            batch,
            html.path());
    String rendered = preview.html(owner, id);
    assertFalse(rendered.toLowerCase(Locale.ROOT).contains("<script"));
    assertEquals(html.sha256(), CollaborationFiles.sha(files.bytes(owner, id)));
  }

  @Test
  @Order(5)
  void realMaterialBrowserWhenExplicitlyEnabled() throws Exception {
    if (!"true".equals(System.getenv("F2R_COLLAB_BROWSER_TEST"))) return;
    Path fixture = Path.of("/tmp/f2r-production-browser.json");
    Files.createFile(
        fixture,
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    Files.writeString(
        fixture,
        json.writeValueAsString(
            Map.of(
                "cookie",
                jwt.issue(owner.userId(), owner.loginId(), owner.role(), owner.credentialStamp()),
                "workspace",
                workspace,
                "reviewWorkspace",
                reviewWorkspace,
                "reviewedCut",
                reviewedCut,
                "reviewedReference",
                reviewedReference,
                "importId",
                batch)));
    try {
      var process =
          new ProcessBuilder("node", "scripts/ai-agent-studio/production-qa.cjs")
              .inheritIO()
              .start();
      assertTrue(process.waitFor(180, TimeUnit.SECONDS));
      assertEquals(0, process.exitValue());
    } finally {
      Files.deleteIfExists(fixture);
    }
  }
}
