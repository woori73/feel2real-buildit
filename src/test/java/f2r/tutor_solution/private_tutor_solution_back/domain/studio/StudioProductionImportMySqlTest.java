package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = {
      "server.port=18090",
      "app.mcp.enabled=true",
      "app.mcp.public-base-url=http://127.0.0.1:18090",
      "app.mcp.internal-base-url=http://127.0.0.1:18090",
      "spring.datasource.username=f2r_test",
      "spring.datasource.password=isolated_test_password",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.show-sql=false",
      "logging.level.org.hibernate.SQL=WARN"
    })
@EnabledIfEnvironmentVariable(named = "F2R_PRODUCTION_SOURCE", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioProductionImportMySqlTest {
  static final String SCHEMA =
      "f2r_assets_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  static final String URL =
      "jdbc:mysql://127.0.0.1:3318/"
          + SCHEMA
          + "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci";
  static final Path STORE = Path.of("/tmp/" + SCHEMA + "-files");

  @DynamicPropertySource
  static void isolated(DynamicPropertyRegistry r) throws Exception {
    try (var c =
            DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3318/mysql", "root", "isolated_test_root");
        var st = c.createStatement()) {
      st.execute("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
      st.execute("GRANT ALL PRIVILEGES ON " + SCHEMA + ".* TO 'f2r_test'@'%'");
    }
    r.add("spring.datasource.url", () -> URL);
    r.add("app.file.upload-dir", () -> STORE.toString());
  }

  @Autowired StudioService studio;
  @Autowired StudioProductionImporter importer;
  @Autowired StudioCatalog workspaceCatalog;
  @Autowired JdbcTemplate db;
  @Autowired UserRepository users;
  @Autowired JwtTokenProvider jwt;
  @Autowired AdminSessionValidator stamps;
  @Autowired ObjectMapper json;
  static AuthenticatedPrincipal admin, editor, stranger;
  static final Map<String, String> files = new HashMap<>();
  static Map<String, Long> before;

  AuthenticatedPrincipal user(String login, Role role) {
    var u =
        users.save(
            User.builder()
                .loginId(login)
                .password("{isolated-test-only}")
                .name("격리 제작 자료 검증")
                .nickname(login)
                .role(role)
                .build());
    return new AuthenticatedPrincipal(
        u.getUserId(), u.getLoginId(), u.getRole().name(), stamps.stampFor(u.getUserId()));
  }

  Map<String, Long> counts() {
    var counts = new LinkedHashMap<String, Long>();
    for (var table : List.of("studio_files", "studio_items", "studio_revisions", "studio_events"))
      counts.put(table, db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
    return counts;
  }

  StudioProductionImporter.Request request() {
    return new StudioProductionImporter.Request(StudioProductionImporter.COMMIT, files);
  }

  @Test
  @Order(1)
  void givenPinned536Sources_whenStaged_thenEveryShaMatchesAndOnly316Objects() throws Exception {
    admin = user("studio-assets-admin", Role.ADMIN);
    editor = user("studio-assets-editor", Role.TUTOR);
    stranger = user("studio-assets-stranger", Role.TUTOR);
    studio.initialize(admin);
    studio.member(admin, editor.userId(), "EDITOR");
    var catalog = importer.catalog();
    assertEquals(536, catalog.files().size());
    var root = Path.of(System.getenv("F2R_PRODUCTION_SOURCE")).toRealPath();
    for (var source : catalog.files()) {
      var path = root.resolve(source.path()).normalize();
      assertTrue(path.startsWith(root));
      assertFalse(Files.isSymbolicLink(path));
      var data = Files.readAllBytes(path);
      assertEquals(source.size(), data.length);
      assertEquals(source.sha256(), HexFormat.of().formatHex(StudioService.digest().digest(data)));
      if (files.containsKey(source.sha256())) continue;
      var result =
          studio.upload(
              admin,
              new MockMultipartFile(
                  "file", path.getFileName().toString(), "application/octet-stream", data));
      var file = (Map<?, ?>) result.get("file");
      assertEquals(source.sha256(), file.get("sha256"));
      files.put(source.sha256(), (String) file.get("id"));
    }
    assertEquals(316, files.size());
    assertEquals(316, studio.files(admin, 200, 0).size() + studio.files(admin, 200, 200).size());
    before = counts();
    assertEquals(0, before.get("studio_revisions"));
  }

  @Test
  @Order(2)
  void given30thCutDatabaseFailure_whenRegistering_thenAllRevisionApprovalAndEventWritesRollback()
      throws Exception {
    var storageBefore = new HashSet<Path>();
    try (var paths = Files.walk(STORE)) {
      paths.filter(Files::isRegularFile).forEach(storageBefore::add);
    }
    fixtureDdl(
        "CREATE TRIGGER assets_fail_cut30 BEFORE INSERT ON studio_revisions FOR EACH ROW BEGIN IF"
            + " NEW.cut_number=30 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated cut30"
            + " failure'; END IF; END");
    try {
      assertThrows(RuntimeException.class, () -> importer.register(admin, request()));
      assertEquals(before, counts());
      assertThrows(RuntimeException.class, () -> importer.register(admin, request()));
      assertEquals(before, counts());
    } finally {
      fixtureDdl("DROP TRIGGER IF EXISTS assets_fail_cut30");
    }
    var storageAfter = new HashSet<Path>();
    try (var paths = Files.walk(STORE)) {
      paths.filter(Files::isRegularFile).forEach(storageAfter::add);
    }
    assertEquals(storageBefore, storageAfter);
  }

  @Test
  @Order(3)
  void givenVerifiedFiles_whenImportedAndRetried_then536Paths68Cuts5ApprovedAndNoDuplicateWrites()
      throws Exception {
    var result = (Map<?, ?>) importer.register(admin, request());
    assertEquals(536, result.get("sourcePaths"));
    assertEquals(678, result.get("createdRevisions"));
    assertEquals(68, studio.cuts(admin, 1).size());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_items i JOIN studio_revisions r ON r.id=i.head_id WHERE"
                + " r.kind='STORYBOARD' AND i.approved_id IS NOT NULL",
            Integer.class));
    var approved =
        db.queryForList(
            "SELECT f.sha256 FROM studio_items i JOIN studio_revisions r ON r.id=i.approved_id JOIN"
                + " studio_files f ON f.id=r.file_id WHERE r.kind='CHARACTER'",
            String.class);
    assertEquals(new HashSet<>(importer.catalog().approvedSha256()), new HashSet<>(approved));
    var after = counts();
    var retry = (Map<?, ?>) importer.register(admin, request());
    assertEquals(0, retry.get("createdRevisions"));
    assertEquals(after, counts());
    for (var f : importer.catalog().files())
      assertEquals(
          f.sha256(),
          HexFormat.of()
              .formatHex(
                  StudioService.digest().digest(studio.bytes(admin, files.get(f.sha256())))));
  }

  @Test
  @Order(4)
  void givenUnauthorizedOrTamperedMapping_whenImported_thenRejectedWithoutChanges()
      throws Exception {
    var prior = counts();
    assertThrows(RuntimeException.class, () -> importer.register(editor, request()));
    assertThrows(RuntimeException.class, () -> importer.register(stranger, request()));
    var changed = new HashMap<>(files);
    var keys = new ArrayList<>(changed.keySet());
    changed.put(keys.get(0), files.get(keys.get(1)));
    assertThrows(
        RuntimeException.class,
        () ->
            importer.register(
                admin,
                new StudioProductionImporter.Request(StudioProductionImporter.COMMIT, changed)));
    assertEquals(prior, counts());
  }

  @Test
  @Order(5)
  void givenPinned536Sources_whenTwoSectionCataloguePaged_thenAllVersionsAndOriginalsRemainReachable() {
    var snapshotQueries = List.of("SELECT * FROM studio_files ORDER BY id",
        "SELECT * FROM studio_items ORDER BY project_id,item_key",
        "SELECT * FROM studio_revisions ORDER BY id", "SELECT * FROM studio_events ORDER BY id");
    var prior = snapshotQueries.stream().map(db::queryForList).toList();
    var rows = new ArrayList<Map<String, Object>>();
    for (int offset = 0; ; offset += 100) {
      var page = workspaceCatalog.list(admin, "materials", "", "", "", "", "all", "", 100, offset);
      rows.addAll(page);
      if (page.size() < 100) break;
    }
    var expected = new HashSet<>(db.queryForList("SELECT id FROM studio_revisions", String.class));
    var actual = new HashSet<String>();
    var reachableFiles = new HashSet<String>();
    for (var row : rows) {
      if ("REVISION".equals(row.get("entry_type"))) assertTrue(actual.add((String) row.get("id")));
      if (row.get("file_id") != null) reachableFiles.add((String) row.get("file_id"));
    }
    assertEquals(678, expected.size());
    assertEquals(expected, actual);
    assertEquals(new HashSet<>(files.values()), reachableFiles);
    assertEquals(316, reachableFiles.size());
    var approved = workspaceCatalog.list(admin, "review", "", "", "", "APPROVED", "all", "", 100, 0);
    assertEquals(new HashSet<>(importer.catalog().approvedSha256()),
        new HashSet<>(approved.stream().map(r -> (String) r.get("sha256")).toList()));
    assertEquals(prior, snapshotQueries.stream().map(db::queryForList).toList());
  }

  @Test
  @Order(99)
  void givenCompletedImport_whenHandoffExported_thenLocalBrowserCanRestoreSameDatabase()
      throws Exception {
    var f =
        Map.of(
            "adminCookie",
            cookie(admin),
            "editorCookie",
            cookie(editor),
            "strangerCookie",
            cookie(stranger),
            "jdbcUrl",
            URL,
            "storagePath",
            STORE.toString(),
            "commit",
            StudioProductionImporter.COMMIT,
            "files",
            files);
    Files.writeString(Path.of("/tmp/f2r-assets-browser-fixture.json"), json.writeValueAsString(f));
    Files.writeString(
        Path.of("/tmp/f2r-assets-registration-request.json"), json.writeValueAsString(request()));
    assertEquals(610, db.queryForObject("SELECT COUNT(*) FROM studio_items", Integer.class));
  }

  void fixtureDdl(String sql) throws Exception {
    // Isolated test fixture DDL only; the application account never gains SUPER privileges.
    try (var c = DriverManager.getConnection(URL, "root", "isolated_test_root");
        var st = c.createStatement()) {
      st.execute(sql);
    }
  }

  String cookie(AuthenticatedPrincipal p) {
    return "ACCESS_TOKEN=" + jwt.issue(p.userId(), p.loginId(), p.role(), p.credentialStamp());
  }
}
