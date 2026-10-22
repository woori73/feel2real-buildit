package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = {
      "server.port=18088",
      "app.mcp.enabled=true",
      "app.mcp.public-base-url=http://127.0.0.1:18088",
      "app.mcp.internal-base-url=http://127.0.0.1:18088",
      "app.mcp.redirect-uris=https://claude.ai/api/mcp/auth_callback,https://chatgpt.com/connector/oauth/isolated-test-callback",
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3318/f2r_studio_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci",
      "spring.datasource.username=f2r_test",
      "spring.datasource.password=isolated_test_password",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.show-sql=false",
      "app.file.upload-dir=/tmp/f2r-studio-test-files",
      "logging.level.org.hibernate.SQL=WARN"
    })
@EnabledIfEnvironmentVariable(named = "F2R_STUDIO_MYSQL_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpStudioMySqlTest {
  @Autowired StudioService studio;
  @Autowired f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioBulkImport bulk;

  @Autowired
  f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository
      academies;

  @Autowired
  f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository
      businesses;

  @Autowired
  f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService access;

  @Autowired McpOAuth oauth;
  @Autowired McpCatalog catalog;
  @Autowired McpGateway gateway;
  @Autowired McpConcurrency concurrency;
  @Autowired UserRepository users;
  @Autowired JwtTokenProvider jwt;
  @Autowired AdminSessionValidator stamps;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired org.springframework.context.ApplicationContext context;
  static JwtTokenProvider.AuthenticatedPrincipal admin, editor, stranger, viewer;
  static String fileId, originalId, proposalId;
  final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  final String base = "http://127.0.0.1:18088";

  JwtTokenProvider.AuthenticatedPrincipal user(String login, Role role) {
    var u =
        users
            .findByLoginId(login)
            .orElseGet(
                () ->
                    users.save(
                        User.builder()
                            .loginId(login)
                            .password("{synthetic-test-only}")
                            .name("격리 테스트")
                            .nickname(login)
                            .role(role)
                            .build()));
    return new JwtTokenProvider.AuthenticatedPrincipal(
        u.getUserId(), u.getLoginId(), u.getRole().name(), stamps.stampFor(u.getUserId()));
  }

  String cookie(JwtTokenProvider.AuthenticatedPrincipal p) {
    return "ACCESS_TOKEN=" + jwt.issue(p.userId(), p.loginId(), p.role(), p.credentialStamp());
  }

  HttpResponse<String> get(String path, String auth) throws Exception {
    var b = HttpRequest.newBuilder(URI.create(base + path)).GET();
    if (auth != null) b.header("Authorization", "Bearer " + auth);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> post(String path, String body, String type, String auth) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", type)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (auth != null) b.header("Authorization", "Bearer " + auth);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  StudioService.Revision revision(String parent, String label, String origin, String content) {
    return new StudioService.Revision(
        "ep01-script",
        parent,
        label,
        "SCRIPT",
        1,
        null,
        origin,
        "검증용 EP01 각본",
        content,
        "테스트 대사",
        "테스트 연출",
        "READY",
        fileId);
  }

  @Test
  @Order(1)
  void givenIsolatedUsers_whenProjectInitialized_thenEmpty14Episodes68Cuts() throws Exception {
    for (String table :
        List.of(
            "mcp_audit",
            "mcp_codes",
            "mcp_tokens",
            "mcp_pending",
            "mcp_grants",
            "mcp_clients",
            "mcp_rate_limits",
            "studio_events",
            "studio_revisions",
            "studio_items",
            "studio_files",
            "studio_members",
            "studio_projects")) db.update("DELETE FROM " + table);
    admin = user("studio-test-admin", Role.ADMIN);
    editor = user("studio-test-editor", Role.TUTOR);
    stranger = user("studio-test-stranger", Role.TUTOR);
    viewer = user("studio-test-viewer", Role.STUDENT);
    studio.initialize(admin);
    studio.member(admin, editor.userId(), "EDITOR");
    studio.member(admin, viewer.userId(), "VIEWER");
    assertEquals(14, studio.overview(admin).get("episodes"));
    assertEquals(68, studio.overview(admin).get("ep01Cuts"));
    assertThrows(Exception.class, () -> studio.overview(stranger));
    assertThrows(Exception.class, () -> studio.member(editor, stranger.userId(), "APPROVER"));
  }

  @Test
  @Order(2)
  void givenOriginal_whenUploadedTwiceAndRevised_thenBytesImmutableAndAiNeedsApproval()
      throws Exception {
    byte[] bytes = "# SYNTHETIC TEST ONLY\n실제 EP01 자료가 아닙니다.".getBytes(StandardCharsets.UTF_8);
    var first =
        studio.upload(
            editor, new MockMultipartFile("file", "test-ep01.md", "text/markdown", bytes));
    fileId = (String) ((Map<?, ?>) first.get("file")).get("id");
    assertEquals(
        true,
        studio
            .upload(editor, new MockMultipartFile("file", "duplicate.md", "text/markdown", bytes))
            .get("duplicate"));
    assertArrayEquals(bytes, studio.bytes(editor, fileId));
    originalId =
        (String) studio.save(editor, revision(null, "r14", "ORIGINAL", "정본 테스트")).get("id");
    proposalId =
        (String) studio.save(editor, revision(originalId, "r2", "AI_PROPOSAL", "제안 테스트")).get("id");
    assertNull(studio.revision(editor, proposalId).get("approved_id"));
    assertThrows(Exception.class, () -> studio.review(editor, proposalId, "APPROVE", "승인 불가"));
    studio.review(admin, originalId, "APPROVE", "원작 확인");
    assertEquals(originalId, studio.revision(admin, proposalId).get("approved_id"));
    assertThrows(
        Exception.class, () -> studio.save(editor, revision(originalId, "r3", "USER_EDIT", "경합")));
    assertThrows(
        Exception.class,
        () -> studio.save(viewer, revision(proposalId, "r3", "USER_EDIT", "열람자 쓰기")));
    assertArrayEquals(bytes, studio.bytes(admin, fileId));
    var request =
        HttpRequest.newBuilder(URI.create(base + "/api/studio/files/" + fileId + "/content"))
            .header("Cookie", cookie(admin))
            .GET()
            .build();
    var downloaded = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, downloaded.statusCode());
    assertArrayEquals(bytes, downloaded.body());
  }

  String connect(String callback, JwtTokenProvider.AuthenticatedPrincipal p, Set<String> scopes)
      throws Exception {
    var c =
        oauth.register(
            Map.of(
                "client_name",
                callback.contains("claude") ? "Claude protocol test" : "ChatGPT protocol test",
                "redirect_uris",
                List.of(callback)));
    String client = (String) c.get("client_id"), verifier = "a".repeat(43);
    var q =
        Map.of(
            "client_id",
            client,
            "redirect_uri",
            callback,
            "response_type",
            "code",
            "state",
            "test-state",
            "code_challenge_method",
            "S256",
            "code_challenge",
            McpOAuth.challenge(verifier),
            "resource",
            base + "/mcp");
    String nonce = oauth.pending(p, q);
    String redirect = oauth.authorize(p, nonce, scopes, true);
    String code = redirect.substring(redirect.indexOf("code=") + 5, redirect.indexOf("&state="));
    var tokenArgs =
        Map.of(
            "grant_type",
            "authorization_code",
            "client_id",
            client,
            "redirect_uri",
            callback,
            "code",
            code,
            "code_verifier",
            verifier,
            "resource",
            base + "/mcp");
    var result = oauth.token(tokenArgs);
    assertThrows(Exception.class, () -> oauth.token(tokenArgs));
    if (scopes.contains("offline_access")) {
      String refresh = (String) result.get("refresh_token");
      var refreshed =
          oauth.token(
              Map.of(
                  "grant_type",
                  "refresh_token",
                  "client_id",
                  client,
                  "refresh_token",
                  refresh,
                  "resource",
                  base + "/mcp"));
      assertNotEquals(result.get("access_token"), refreshed.get("access_token"));
    }
    return (String) result.get("access_token");
  }

  JsonNode rpc(String token, String tool, Object args) throws Exception {
    var r =
        post(
            "/mcp",
            json.writeValueAsString(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    1,
                    "method",
                    "tools/call",
                    "params",
                    Map.of("name", tool, "arguments", args))),
            "application/json",
            token);
    assertEquals(200, r.statusCode(), r.body());
    return json.readTree(r.body()).get("result");
  }

  @Test
  @Order(3)
  void givenTwoOAuthClients_whenReadingSameStoredAsset_thenIdenticalContentAndReadOnly()
      throws Exception {
    String chatgpt =
        connect(
            "https://chatgpt.com/connector/oauth/isolated-test-callback",
            editor,
            Set.of("catalog:read", "studio:read", "offline_access"));
    String claude =
        connect(
            "https://claude.ai/api/mcp/auth_callback",
            editor,
            Set.of("catalog:read", "studio:read"));
    var args =
        Map.of(
            "endpointId", "GET /api/studio/revisions/{id}", "pathParams", Map.of("id", originalId));
    var a = rpc(chatgpt, "read_studio", args);
    var b = rpc(claude, "read_studio", args);
    assertFalse(a.get("isError").asBoolean(), a.toString());
    assertEquals(a, b);
    assertTrue(
        rpc(chatgpt, "read_studio", Map.of("endpointId", "POST /api/studio/revisions"))
            .get("isError")
            .asBoolean());
    assertTrue(
        rpc(chatgpt, "read_easy", Map.of("endpointId", "GET /api/admin/easy/transactions"))
            .get("isError")
            .asBoolean());
    oauth.revoke(chatgpt);
    assertEquals(
        401,
        post(
                "/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}",
                "application/json",
                chatgpt)
            .statusCode());
    var bad =
        rpc(
            claude,
            "read_studio",
            Map.of(
                "endpointId",
                "GET /api/studio/files/{id}",
                "pathParams",
                Map.of("id", "../users")));
    assertTrue(bad.get("isError").asBoolean());
  }

  @Test
  @Order(4)
  void givenNoGrantOrForeignUser_whenReading_thenDeniedAndMetadataDiscoverable() throws Exception {
    assertEquals(200, get("/.well-known/oauth-protected-resource/mcp", null).statusCode());
    var anon =
        post(
            "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", "application/json", null);
    assertEquals(401, anon.statusCode());
    assertTrue(
        anon.headers().firstValue("www-authenticate").orElse("").contains("resource_metadata"));
    String strangerToken =
        connect("https://claude.ai/api/mcp/auth_callback", stranger, Set.of("studio:read"));
    assertTrue(
        rpc(strangerToken, "read_studio", Map.of("endpointId", "GET /api/studio/files"))
            .get("isError")
            .asBoolean());
    var req =
        HttpRequest.newBuilder(URI.create(base + "/api/studio/files/" + fileId + "/content"))
            .header("Cookie", cookie(stranger))
            .GET()
            .build();
    assertEquals(403, http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
    assertThrows(
        Exception.class,
        () -> oauth.register(Map.of("redirect_uris", List.of("https://evil.example/callback"))));
  }

  @Test
  @Order(5)
  void givenPathBoundDelegation_whenReusedOnWriteOrAnotherPath_thenDenied() throws Exception {
    String token = jwt.issueReadOnly(editor, "/api/studio/files");
    var req =
        HttpRequest.newBuilder(URI.create(base + "/api/studio/revisions"))
            .header("Cookie", "ACCESS_TOKEN=" + token)
            .GET()
            .build();
    assertEquals(403, http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
    req =
        HttpRequest.newBuilder(URI.create(base + "/api/studio/files"))
            .header("Cookie", "ACCESS_TOKEN=" + token)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    assertEquals(403, http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  @Test
  @Order(6)
  void givenRealOAuthHttpFlow_whenConsentAndPkceExchanged_thenBoundAndSingleUse() throws Exception {
    String callback = "https://claude.ai/api/mcp/auth_callback", verifier = "b".repeat(43);
    var registration =
        post(
            "/mcp/oauth/register",
            json.writeValueAsString(
                Map.of("client_name", "HTTP OAuth test", "redirect_uris", List.of(callback))),
            "application/json",
            null);
    assertEquals(200, registration.statusCode());
    String client = json.readTree(registration.body()).get("client_id").asText();
    var q =
        Map.of(
            "client_id",
            client,
            "redirect_uri",
            callback,
            "response_type",
            "code",
            "state",
            "http-test-state",
            "code_challenge_method",
            "S256",
            "code_challenge",
            McpOAuth.challenge(verifier),
            "resource",
            base + "/mcp");
    String query =
        q.entrySet().stream()
            .map(e -> McpOAuth.encode(e.getKey()) + "=" + McpOAuth.encode(e.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));
    var page =
        http.send(
            HttpRequest.newBuilder(URI.create(base + "/mcp/oauth/authorize?" + query))
                .header("Cookie", cookie(admin))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, page.statusCode());
    String nonce = org.jsoup.Jsoup.parse(page.body()).select("input[name=nonce]").attr("value");
    assertFalse(nonce.isBlank());
    String form = "nonce=" + McpOAuth.encode(nonce) + "&scopes=catalog%3Aread&scopes=studio%3Aread";
    var request =
        HttpRequest.newBuilder(URI.create(base + "/mcp/oauth/authorize"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", cookie(admin))
            .POST(HttpRequest.BodyPublishers.ofString(form));
    assertEquals(
        403, http.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    var consent =
        http.send(request.header("Origin", base).build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(303, consent.statusCode());
    String location = consent.headers().firstValue("location").orElseThrow();
    String code = location.substring(location.indexOf("code=") + 5, location.indexOf("&state="));
    Map<String, String> exchange =
        new LinkedHashMap<>(
            Map.of(
                "client_id",
                client,
                "redirect_uri",
                callback,
                "grant_type",
                "authorization_code",
                "code",
                code,
                "code_verifier",
                "c".repeat(43),
                "resource",
                base + "/mcp"));
    java.util.function.Function<Map<String, String>, String> encoded =
        m ->
            m.entrySet().stream()
                .map(e -> McpOAuth.encode(e.getKey()) + "=" + McpOAuth.encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    assertEquals(
        400,
        post("/mcp/oauth/token", encoded.apply(exchange), "application/x-www-form-urlencoded", null)
            .statusCode());
    exchange.put("code_verifier", verifier);
    var result =
        post(
            "/mcp/oauth/token", encoded.apply(exchange), "application/x-www-form-urlencoded", null);
    assertEquals(200, result.statusCode());
    assertEquals(
        400,
        post("/mcp/oauth/token", encoded.apply(exchange), "application/x-www-form-urlencoded", null)
            .statusCode());
    String access = json.readTree(result.body()).get("access_token").asText();
    assertFalse(
        rpc(access, "read_studio", Map.of("endpointId", "GET /api/studio/project"))
            .get("isError")
            .asBoolean());
    db.update(
        "UPDATE mcp_tokens SET expires_at=TIMESTAMP('2000-01-01 00:00:00') WHERE hash=?",
        McpOAuth.hash(access));
    assertThrows(Exception.class, () -> oauth.authenticate("Bearer " + access));
  }

  @Test
  @Order(7)
  void givenDelegatedGet_whenJdbcTriesToWrite_thenNoBusinessDataCommitted() throws Exception {
    var filter =
        new f2r.tutor_solution.private_tutor_solution_back.config.security
            .McpReadOnlyTransactionFilter(
            context.getBeanProvider(
                org.springframework.transaction.PlatformTransactionManager.class));
    var request =
        new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/studio/project");
    request.setAttribute("f2r.mcp.readOnly", true);
    String before =
        db.queryForObject(
            "SELECT title FROM studio_projects WHERE id=?", String.class, StudioService.PROJECT);
    try {
      filter.doFilter(
          request,
          new org.springframework.mock.web.MockHttpServletResponse(),
          (req, res) -> {
            assertTrue(
                db.execute(
                    (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                        java.sql.Connection::isReadOnly));
            db.update(
                "UPDATE studio_projects SET title='forbidden' WHERE id=?", StudioService.PROJECT);
          });
    } catch (org.springframework.dao.DataAccessException expected) {
      /* MySQL rejects DML in a read-only transaction. */
    }
    assertEquals(
        before,
        db.queryForObject(
            "SELECT title FROM studio_projects WHERE id=?", String.class, StudioService.PROJECT));
  }

  @Test
  @Order(8)
  void givenMemberRevocationAndRefreshReplay_whenPreviouslyGranted_thenAccessStops()
      throws Exception {
    String token =
        connect("https://claude.ai/api/mcp/auth_callback", editor, Set.of("studio:read"));
    studio.member(admin, editor.userId(), "REVOKE");
    assertTrue(
        rpc(token, "read_studio", Map.of("endpointId", "GET /api/studio/files"))
            .get("isError")
            .asBoolean());
    studio.member(admin, editor.userId(), "EDITOR");
    var c =
        oauth.register(Map.of("redirect_uris", List.of("https://claude.ai/api/mcp/auth_callback")));
    String client = (String) c.get("client_id"), verifier = "d".repeat(43);
    var q =
        Map.of(
            "client_id",
            client,
            "redirect_uri",
            "https://claude.ai/api/mcp/auth_callback",
            "response_type",
            "code",
            "state",
            "refresh-test",
            "code_challenge_method",
            "S256",
            "code_challenge",
            McpOAuth.challenge(verifier),
            "resource",
            base + "/mcp");
    String location =
        oauth.authorize(editor, oauth.pending(editor, q), Set.of("studio:read", "offline_access"));
    String code = location.substring(location.indexOf("code=") + 5, location.indexOf("&state="));
    var tokens =
        oauth.token(
            Map.of(
                "grant_type",
                "authorization_code",
                "client_id",
                client,
                "redirect_uri",
                q.get("redirect_uri"),
                "code",
                code,
                "code_verifier",
                verifier,
                "resource",
                base + "/mcp"));
    var refresh =
        Map.of(
            "grant_type",
            "refresh_token",
            "client_id",
            client,
            "refresh_token",
            (String) tokens.get("refresh_token"),
            "resource",
            base + "/mcp");
    var renewed = oauth.token(refresh);
    assertThrows(Exception.class, () -> oauth.token(refresh));
    assertThrows(
        Exception.class, () -> oauth.authenticate("Bearer " + renewed.get("access_token")));
  }

  @Test
  @Order(9)
  void givenRateLimit_whenExceeded_thenRejected() {
    var limit = new McpRateLimit(db);
    limit.check("test-limit", 2);
    limit.check("test-limit", 2);
    assertThrows(Exception.class, () -> limit.check("test-limit", 2));
  }

  @Test
  @Order(10)
  void givenOldFileVersion_whenRestored_thenAppendsAndPreservesOriginalApproval() throws Exception {
    var before = studio.revision(editor, originalId);
    byte[] original = studio.bytes(editor, fileId);
    var restored = studio.restore(editor, originalId, proposalId, "restored-original");
    assertNotEquals(originalId, restored.get("id"));
    assertEquals("USER_EDIT", restored.get("origin"));
    assertEquals(originalId, restored.get("approved_id"));
    assertEquals(fileId, restored.get("file_id"));
    var after = studio.revision(editor, originalId);
    for (String key : List.of("content", "file_id", "origin", "parent_id", "created_at"))
      assertEquals(before.get(key), after.get(key));
    assertArrayEquals(original, studio.bytes(editor, fileId));
    assertThrows(
        Exception.class,
        () -> studio.restore(viewer, originalId, (String) restored.get("id"), "denied"));
    assertThrows(Exception.class, () -> studio.restore(editor, originalId, proposalId, "stale"));
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_events WHERE action='RESTORE' AND revision_id=?",
            Long.class,
            restored.get("id")));
  }

  byte[] manifest(String marker) {
    var cuts = new ArrayList<Object>();
    for (int i = 1; i <= 68; i++)
      cuts.add(
          Map.of(
              "cutNumber",
              i,
              "title",
              "SYNTHETIC " + marker + " " + i,
              "content",
              "실제 EP01 원작이 아닌 격리 시험용 " + i,
              "dialogue",
              "합성 대사 " + i));
    return json.writeValueAsBytes(Map.of("episode", 1, "cuts", cuts));
  }

  @Test
  @Order(11)
  void given68CutJsonAndMoreThan200History_whenImported_thenNoCutsDisappearAndNoAutoApproval()
      throws Exception {
    var upload =
        studio.upload(
            editor,
            new MockMultipartFile(
                "file", "synthetic-68.json", "application/json", manifest("JSON")));
    String source = (String) ((Map<?, ?>) upload.get("file")).get("id");
    var imported = (Map<?, ?>) bulk.importEp01(editor, source);
    assertEquals(68, imported.get("registered"));
    var cuts = studio.cuts(editor, 1);
    assertEquals(68, cuts.size());
    assertTrue(cuts.stream().allMatch(c -> c.get("approved_id") == null));
    var first = cuts.getFirst();
    String parent = (String) first.get("id");
    for (int i = 0; i < 205; i++)
      parent =
          (String)
              studio
                  .save(
                      editor,
                      new StudioService.Revision(
                          (String) first.get("item_key"),
                          parent,
                          "history-" + i,
                          "STORYBOARD",
                          1,
                          1,
                          "USER_EDIT",
                          "버전 시험",
                          "합성",
                          "",
                          "",
                          "REVIEW",
                          source))
                  .get("id");
    assertEquals(200, studio.revisions(editor, 1, "STORYBOARD", null).size());
    assertTrue(studio.revisions(editor, 1, "STORYBOARD", null, 200, 200).size() > 0);
    assertEquals(68, studio.cuts(editor, 1).size());
    assertThrows(Exception.class, () -> bulk.importEp01(editor, source));
    assertEquals(68, studio.cuts(editor, 1).size());
    assertArrayEquals(manifest("JSON"), studio.bytes(editor, source));
  }

  @Test
  @Order(12)
  void given68CutZipAndTraversal_whenImported_thenVersionsAppendAndUnsafeZipDenied()
      throws Exception {
    var buffer = new java.io.ByteArrayOutputStream();
    var zipManifest = (tools.jackson.databind.node.ObjectNode) json.readTree(manifest("ZIP"));
    ((tools.jackson.databind.node.ObjectNode) zipManifest.path("cuts").get(0))
        .put("file", "assets/cut01.md");
    byte[] asset = "SYNTHETIC ZIP ASSET".getBytes(StandardCharsets.UTF_8);
    try (var zip = new java.util.zip.ZipOutputStream(buffer)) {
      zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json"));
      zip.write(json.writeValueAsBytes(zipManifest));
      zip.closeEntry();
      zip.putNextEntry(new java.util.zip.ZipEntry("assets/cut01.md"));
      zip.write(asset);
      zip.closeEntry();
    }
    var upload =
        studio.upload(
            editor,
            new MockMultipartFile(
                "file", "synthetic-68.zip", "application/zip", buffer.toByteArray()));
    String source = (String) ((Map<?, ?>) upload.get("file")).get("id");
    assertEquals(68, ((Map<?, ?>) bulk.importEp01(editor, source)).get("registered"));
    assertArrayEquals(
        asset, studio.bytes(editor, (String) studio.cuts(editor, 1).getFirst().get("file_id")));
    assertArrayEquals(buffer.toByteArray(), studio.bytes(editor, source));
    buffer.reset();
    try (var zip = new java.util.zip.ZipOutputStream(buffer)) {
      zip.putNextEntry(new java.util.zip.ZipEntry("../escape.md"));
      zip.write("unsafe".getBytes());
      zip.closeEntry();
    }
    var unsafe =
        studio.upload(
            editor,
            new MockMultipartFile(
                "file", "traversal.zip", "application/zip", buffer.toByteArray()));
    long count = db.queryForObject("SELECT COUNT(*) FROM studio_revisions", Long.class);
    assertThrows(
        Exception.class,
        () -> bulk.importEp01(editor, (String) ((Map<?, ?>) unsafe.get("file")).get("id")));
    assertEquals(count, db.queryForObject("SELECT COUNT(*) FROM studio_revisions", Long.class));
    buffer.reset();
    try (var zip = new java.util.zip.ZipOutputStream(buffer)) {
      zip.putNextEntry(new java.util.zip.ZipEntry("oversized-directory/"));
      zip.write(new byte[21 * 1024 * 1024]);
      zip.closeEntry();
    }
    var oversized =
        studio.upload(
            editor,
            new MockMultipartFile(
                "file", "directory-payload.zip", "application/zip", buffer.toByteArray()));
    var limit =
        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> bulk.importEp01(editor, (String) ((Map<?, ?>) oversized.get("file")).get("id")));
    assertEquals(413, limit.getStatusCode().value());
    assertEquals(count, db.queryForObject("SELECT COUNT(*) FROM studio_revisions", Long.class));
  }

  @Test
  @Order(13)
  void givenOfficialCallbackAndSpoofs_whenRegistering_thenOnlyExactConfiguredRedirectAllowed() {
    for (String uri :
        List.of(
            "https://chatgpt.com/connector_platform/oauth/callback",
            "https://chat.openai.com/connector_platform/oauth/callback",
            "https://chatgpt.com.evil.example/connector/oauth/isolated-test-callback",
            "https://chatgpt.com/connector/oauth/unregistered",
            "https://chatgpt.com/connector/oauth/isolated-test-callback?next=evil",
            "https://chatgpt.com/connector/oauth/isolated-test-callback#fragment",
            "http://chatgpt.com/connector/oauth/isolated-test-callback"))
      assertThrows(Exception.class, () -> oauth.register(Map.of("redirect_uris", List.of(uri))));
    assertNotNull(
        oauth.register(
            Map.of(
                "redirect_uris",
                List.of("https://chatgpt.com/connector/oauth/isolated-test-callback"))));
  }

  @Test
  @Order(14)
  void givenProxyAndUsers_whenLimited_thenIndependentUserBudgetsAndBoundedServerCalls() {
    var rates = new McpRateLimit(db);
    for (int i = 0; i < 60; i++) rates.check("user-proxy-test:A", 60);
    assertThrows(Exception.class, () -> rates.check("user-proxy-test:A", 60));
    assertDoesNotThrow(() -> rates.check("user-proxy-test:B", 60));
    var resolver =
        new f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller
            .AdmissionsRateLimiter(10, 60, 100, "10.0.0.0/8", 500);
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setRemoteAddr("10.1.1.1");
    request.addHeader("X-Forwarded-For", "203.0.113.99,198.51.100.2");
    assertEquals("198.51.100.2", resolver.resolveClientIp(request));
    request.setRemoteAddr("198.51.100.3");
    assertEquals("198.51.100.3", resolver.resolveClientIp(request));
    var calls = new McpConcurrency(1);
    assertTrue(calls.tryAcquire());
    assertFalse(calls.tryAcquire());
    calls.release();
    assertTrue(calls.tryAcquire());
    calls.release();
  }

  @Test
  @Order(15)
  void givenFinanceScope_whenReading_thenOnlyOwnedBusinessSummaryAndNoRawOrCommitTools()
      throws Exception {
    var owner = users.findById(editor.userId()).orElseThrow();
    var academy =
        academies
            .findByTutor_UserId(editor.userId())
            .orElseGet(
                () ->
                    academies.save(
                        f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy
                            .builder()
                            .tutor(owner)
                            .slug("studio-finance-test")
                            .name("합성 학원")
                            .logoStorageKey("test")
                            .intro("synthetic")
                            .build()));
    var biz =
        businesses.findAll().stream()
            .filter(b -> "mcp-owned-test".equals(b.getCode()))
            .findFirst()
            .orElseGet(
                () ->
                    businesses.save(
                        f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity
                            .EasyBusiness.builder()
                            .code("mcp-owned-test")
                            .name("합성 사업체")
                            .active(true)
                            .build()));
    access.link(academy.getAcademyId(), biz.getBusinessId());
    String token = connect("https://claude.ai/api/mcp/auth_callback", editor, Set.of("easy:read"));
    var r =
        rpc(
            token,
            "read_easy",
            Map.of(
                "endpointId",
                "GET /api/mcp/finance/summary",
                "query",
                Map.of("businessId", biz.getBusinessId().toString())));
    assertFalse(r.path("isError").asBoolean(), r.toString());
    String value = r.path("content").get(0).path("text").asText();
    assertFalse(value.contains("recentTransactions"));
    assertFalse(value.contains("overdueStudents"));
    assertFalse(value.contains("rawJson"));
    String foreign =
        connect("https://claude.ai/api/mcp/auth_callback", stranger, Set.of("easy:read"));
    assertTrue(
        rpc(
                foreign,
                "read_easy",
                Map.of(
                    "endpointId",
                    "GET /api/mcp/finance/summary",
                    "query",
                    Map.of("businessId", biz.getBusinessId().toString())))
            .path("isError")
            .asBoolean());
    for (String endpoint :
        List.of(
            "GET /api/admin/easy/transactions",
            "GET /api/admin/publishing/maintenance",
            "GET /api/dashboard/assistant/v1/drafts",
            "GET /api/tutor/easy/imports/{batchId}",
            "POST /api/dashboard/assistant/v1/drafts/x/approve",
            "POST /api/admin/easy/financial-collector/previews/{id}/approve-and-commit"))
      assertThrows(Exception.class, () -> catalog.policy(endpoint));
  }

  @Test
  @Order(16)
  void givenAdminSensitiveScopes_whenNoSeparateAcknowledgement_thenDenied() {
    var client =
        oauth.register(Map.of("redirect_uris", List.of("https://claude.ai/api/mcp/auth_callback")));
    var q =
        Map.of(
            "client_id",
            (String) client.get("client_id"),
            "redirect_uri",
            "https://claude.ai/api/mcp/auth_callback",
            "response_type",
            "code",
            "state",
            "sensitive-test",
            "code_challenge_method",
            "S256",
            "code_challenge",
            McpOAuth.challenge("z".repeat(43)),
            "resource",
            base + "/mcp",
            "scope",
            "easy:read");
    String nonce = oauth.pending(admin, q);
    assertThrows(Exception.class, () -> oauth.authorize(admin, nonce, Set.of("easy:read")));
    assertNotNull(oauth.authorize(admin, nonce, Set.of("easy:read"), true));
    assertTrue(
        db.queryForObject(
                "SELECT COUNT(*) FROM mcp_audit WHERE tool='oauth_consent' AND"
                    + " outcome='SENSITIVE_ACK'",
                Long.class)
            > 0);
  }

  @Test
  @Order(17)
  void givenAllServerPermitsHeld_whenCatalogRequested_then429AndRecoversAfterRelease()
      throws Exception {
    String token =
        connect("https://claude.ai/api/mcp/auth_callback", admin, Set.of("catalog:read"));
    int held = 0;
    try {
      while (concurrency.tryAcquire()) held++;
      var response =
          post(
              "/mcp",
              "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
              "application/json",
              token);
      assertEquals(429, response.statusCode());
      assertTrue(response.body().contains("server_concurrency_limit"));
    } finally {
      for (int i = 0; i < held; i++) concurrency.release();
    }
    assertEquals(
        200,
        post(
                "/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                "application/json",
                token)
            .statusCode());
  }

  @Test
  @Order(18)
  void givenPublicRegistration_whenAuthorizationHeaderRotates_thenIpQuotaCannotBeBypassed()
      throws Exception {
    db.update(
        "DELETE FROM mcp_rate_limits WHERE bucket_key=?",
        McpOAuth.hash("/mcp/oauth/register:127.0.0.1"));
    String body = "{\"redirect_uris\":[\"https://claude.ai/api/mcp/auth_callback\"]}";
    for (int i = 0; i < 5; i++)
      assertEquals(
          200, post("/mcp/oauth/register", body, "application/json", "fake-" + i).statusCode());
    var denied = post("/mcp/oauth/register", body, "application/json", "fake-new-header");
    assertEquals(429, denied.statusCode());
    assertEquals("60", denied.headers().firstValue("Retry-After").orElseThrow());
  }

  @Test
  @Order(19)
  void
      givenSensitiveBinary_whenNoExplicitOriginalConsent_thenDeniedButOptInPreservesBytesAndRights()
          throws Exception {
    byte[] raw =
        "%PDF-1.4 SYNTHETIC +821012345678 account 1234567890".getBytes(StandardCharsets.UTF_8);
    var uploaded =
        studio.upload(
            editor,
            new MockMultipartFile("file", "sensitive-synthetic.pdf", "application/pdf", raw));
    String id = (String) ((Map<?, ?>) uploaded.get("file")).get("id");
    var args =
        Map.<String, Object>of(
            "endpointId", "GET /api/studio/files/{id}/content", "pathParams", Map.of("id", id));
    String defaultToken =
        connect("https://claude.ai/api/mcp/auth_callback", editor, Set.of("studio:read"));
    var denied = rpc(defaultToken, "read_studio", args);
    assertTrue(denied.path("isError").asBoolean(), denied.toString());
    assertTrue(denied.toString().contains("originals_scope_required"));
    assertFalse(denied.toString().contains("base64"));

    var client =
        oauth.register(Map.of("redirect_uris", List.of("https://claude.ai/api/mcp/auth_callback")));
    var q =
        Map.of(
            "client_id",
            (String) client.get("client_id"),
            "redirect_uri",
            "https://claude.ai/api/mcp/auth_callback",
            "response_type",
            "code",
            "state",
            "binary-warning",
            "code_challenge_method",
            "S256",
            "code_challenge",
            McpOAuth.challenge("x".repeat(43)),
            "resource",
            base + "/mcp");
    String nonce = oauth.pending(editor, q);
    assertThrows(
        Exception.class,
        () -> oauth.authorize(editor, nonce, Set.of("studio:read", "originals:read"), false));
    String token =
        connect(
            "https://claude.ai/api/mcp/auth_callback",
            editor,
            Set.of("studio:read", "originals:read"));
    var allowed = rpc(token, "read_studio", args);
    assertFalse(allowed.path("isError").asBoolean(), allowed.toString());
    var payload = json.readTree(allowed.path("content").get(0).path("text").asText());
    assertArrayEquals(raw, Base64.getDecoder().decode(payload.path("base64").asText()));
    String foreign =
        connect(
            "https://claude.ai/api/mcp/auth_callback",
            stranger,
            Set.of("studio:read", "originals:read"));
    assertTrue(rpc(foreign, "read_studio", args).path("isError").asBoolean());
    assertTrue(
        db.queryForObject(
                "SELECT COUNT(*) FROM mcp_audit WHERE user_id=? AND outcome='SENSITIVE_ACK'",
                Long.class,
                editor.userId())
            > 0);
  }

  @Test
  @Order(99)
  void givenSameProxyAndExhaustedUser_whenAnotherUserCalls_thenNoSharedUserQuota()
      throws Exception {
    String a = connect("https://claude.ai/api/mcp/auth_callback", editor, Set.of("studio:read"));
    String b = connect("https://claude.ai/api/mcp/auth_callback", stranger, Set.of("studio:read"));
    db.update(
        "DELETE FROM mcp_rate_limits WHERE bucket_key=?", McpOAuth.hash("user:" + editor.userId()));
    var rates = new McpRateLimit(db);
    for (int i = 0; i < 60; i++) rates.check("user:" + editor.userId(), 60);
    String ping = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
    assertEquals(429, post("/mcp", ping, "application/json", a).statusCode());
    assertEquals(200, post("/mcp", ping, "application/json", b).statusCode());
  }

  @Test
  @Order(100)
  void exportInventoryAndBrowserFixture() throws Exception {
    assertTrue(catalog.inventory().size() > 500);
    Path out = Path.of("/tmp/f2r-route-inventory.json");
    Files.writeString(
        out, json.writerWithDefaultPrettyPrinter().writeValueAsString(catalog.inventory()));
    Files.writeString(
        Path.of("/tmp/f2r-browser-fixture.json"),
        json.writeValueAsString(
            Map.of(
                "adminCookie",
                cookie(admin),
                "editorCookie",
                cookie(editor),
                "strangerCookie",
                cookie(stranger),
                "fileId",
                fileId,
                "originalId",
                originalId,
                "proposalId",
                proposalId)));
  }
}
