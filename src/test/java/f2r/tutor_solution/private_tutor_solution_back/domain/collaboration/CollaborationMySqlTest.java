package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.mcp.McpOAuth;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Real isolated MySQL + OAuth/PKCE + HTTP MCP. No inference clients, mock models, or live AI login.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = {
      "server.port=19088", "spring.datasource.url=jdbc:mysql://127.0.0.1:3319/f2r_cluster_test",
      "spring.datasource.username=f2r_test", "spring.datasource.password=isolated_cluster_password",
      "spring.flyway.locations=classpath:db/migration,classpath:db/dev-seed",
          "spring.jpa.show-sql=false",
      "logging.level.org.hibernate.SQL=WARN",
          "app.file.upload-dir=/tmp/f2r-collaboration-test-files",
      "app.collaboration.public-base-url=http://127.0.0.1:19088", "app.mcp.enabled=true",
      "app.mcp.public-base-url=http://127.0.0.1:19088",
          "app.collaboration.connector-append-enabled=true"
    })
@EnabledIfEnvironmentVariable(named = "F2R_COLLAB_MYSQL_TEST", matches = "true")
class CollaborationMySqlTest {
  @Autowired CollaborationService service;
  @Autowired CollaborationWorkspaces spaces;
  @Autowired CollaborationFiles files;
  @Autowired CollaborationMcp connector;
  @Autowired StudioService studio;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate db;
  @Autowired JwtTokenProvider jwt;
  @Autowired McpOAuth oauth;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager manager;
  AuthenticatedPrincipal owner, other, viewer, stranger;
  String workspace;

  record Connection(String token, McpOAuth.Identity identity) {}

  AuthenticatedPrincipal user(String kind, Role role) {
    String login = "collab-" + kind + "-" + UUID.randomUUID().toString().substring(0, 12);
    var u =
        users.saveAndFlush(
            new User(login, "synthetic-test-password", "격리 검증", login, null, null, role));
    return new AuthenticatedPrincipal(u.getUserId(), login, role.name());
  }

  @BeforeEach
  void setup() {
    var admin = user("admin", Role.ADMIN);
    studio.initialize(admin);
    owner = user("owner", Role.TUTOR);
    other = user("other", Role.TUTOR);
    viewer = user("viewer", Role.TUTOR);
    stranger = user("stranger", Role.TUTOR);
    studio.member(admin, owner.userId(), "EDITOR");
    studio.member(admin, other.userId(), "EDITOR");
    studio.member(admin, viewer.userId(), "VIEWER");
    workspace = spaces.create(owner, "격리 공동 프로젝트").get("id").toString();
    spaces.member(owner, workspace, other.userId(), "EDITOR");
    spaces.member(owner, workspace, viewer.userId(), "VIEWER");
  }

  Connection connection(AuthenticatedPrincipal p, String agent, boolean append, boolean originals)
      throws Exception {
    String redirect = "https://claude.ai/api/mcp/auth_callback", verifier = "v".repeat(64);
    String challenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    var client =
        oauth.register(
            Map.of(
                "client_name",
                "Isolated " + agent,
                "redirect_uris",
                List.of(redirect),
                "token_endpoint_auth_method",
                "none"));
    var scopes = new HashSet<>(Set.of("studio:read"));
    if (append) scopes.add("collaboration:append");
    if (originals) scopes.add("originals:read");
    var q =
        Map.of(
            "client_id",
            client.get("client_id").toString(),
            "redirect_uri",
            redirect,
            "response_type",
            "code",
            "code_challenge_method",
            "S256",
            "code_challenge",
            challenge,
            "resource",
            oauth.resource(),
            "state",
            "isolated-state",
            "scope",
            String.join(" ", scopes));
    String uri = oauth.authorize(p, oauth.pending(p, q), scopes, true);
    String code =
        Arrays.stream(URI.create(uri).getRawQuery().split("&"))
            .filter(x -> x.startsWith("code="))
            .map(x -> URLDecoder.decode(x.substring(5), StandardCharsets.UTF_8))
            .findFirst()
            .orElseThrow();
    String token =
        oauth
            .token(
                Map.of(
                    "grant_type",
                    "authorization_code",
                    "code",
                    code,
                    "code_verifier",
                    verifier,
                    "client_id",
                    client.get("client_id").toString(),
                    "redirect_uri",
                    redirect,
                    "resource",
                    oauth.resource()))
            .get("access_token")
            .toString();
    var identity = oauth.authenticate("Bearer " + token);
    connector.bind(p, new CollaborationMcp.Bind(identity.grantId(), workspace, agent, append));
    return new Connection(token, identity);
  }

  Map<String, Object> task(List<String> agents, int cap) {
    return service.create(
        owner,
        new CollaborationService.Create(
            "격리 협업 검증", "합성 QA이며 실제 EP01 자료가 아닙니다. 정본 보존.", agents, cap, 0, workspace));
  }

  Map<String, Object> change(Map<String, Object> task, String action) {
    return service.change(
        owner,
        task.get("id").toString(),
        new CollaborationService.Change(
            ((Number) task.get("version")).longValue(), action, true, false));
  }

  Map<String, Object> reload(Map<String, Object> task) {
    return service.detail(owner, task.get("id").toString());
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> call(Connection c, String name, Map<String, Object> args) {
    return (Map<String, Object>) connector.execute(c.identity(), name, json.valueToTree(args));
  }

  Map<String, Object> submit(Connection c, Map<String, Object> task, String text, String decision) {
    return call(
        c,
        "collaboration_submit",
        Map.of(
            "requestId",
            UUID.randomUUID().toString(),
            "taskId",
            task.get("id"),
            "version",
            task.get("version"),
            "text",
            text,
            "decision",
            decision));
  }

  void denied(int status, Runnable action) {
    assertEquals(
        status, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
  }

  @Test
  void realOAuthScopesAndExplicitBindingCannotBeBypassed() throws Exception {
    var read = connection(owner, "GPT", false, true);
    assertEquals(
        List.of("collaboration_read"),
        connector.tools(read.identity()).stream().map(t -> t.get("name")).toList());
    denied(403, () -> call(read, "collaboration_add_document", Map.of()));
    denied(
        403,
        () ->
            connector.bind(
                other,
                new CollaborationMcp.Bind(read.identity().grantId(), workspace, "CLAUDE", true)));
    var write = connection(owner, "CLAUDE", true, true);
    denied(
        400,
        () ->
            connector.bind(
                owner,
                new CollaborationMcp.Bind(write.identity().grantId(), workspace, "CLAUDE", false)));
    denied(400, () -> call(write, "collaboration_read", Map.of("workspaceId", "other")));
    denied(400, () -> call(write, "collaboration_read", Map.of("offset", 9223372036854775807L)));
    var view = connection(viewer, "GPT", false, false);
    assertNotNull(call(view, "collaboration_read", Map.of()));
    connector.unbind(viewer, view.identity().grantId());
    assertTrue(connector.tools(view.identity()).isEmpty());
    oauth.revoke(write.token());
    denied(401, () -> oauth.authenticate("Bearer " + write.token()));
  }

  @Test
  void defaultAppendFeatureSwitchFailsClosed() throws Exception {
    var write = connection(owner, "GPT", true, true);
    ReflectionTestUtils.setField(connector, "appendEnabled", false);
    try {
      assertEquals(1, connector.tools(write.identity()).size());
      denied(403, () -> call(write, "collaboration_add_document", Map.of()));
    } finally {
      ReflectionTestUtils.setField(connector, "appendEnabled", true);
    }
    assertFalse(service.agents(owner).toString().contains("serverAutoRun=true"));
    denied(
        400,
        () ->
            service.create(
                owner, new CollaborationService.Create("x", "y", List.of("GPT"), 2, 1, workspace)));
  }

  @Test
  void selectedRolesHandOffDraftsThenClaudeCodexRereviewWithoutStartingModels() throws Exception {
    var gpt = connection(owner, "GPT", true, true);
    var codex = connection(owner, "CODEX", true, true);
    var claude = connection(owner, "CLAUDE", true, true);
    var muse = connection(owner, "MUSE", true, true);
    var task = change(task(List.of("GPT", "CODEX", "CLAUDE"), 8), "start");
    String id = task.get("id").toString();
    assertEquals("WAITING_AGENT", task.get("status"));
    assertEquals(1, service.inbox(owner, workspace, "GPT").size());
    denied(403, () -> submit(muse, task, "unassigned", "INFO"));
    denied(409, () -> submit(claude, task, "out of order", "PASS"));
    submit(gpt, task, "공동 문서 초안", "INFO");
    submit(codex, reload(task), "수정안", "INFO");
    submit(claude, reload(task), "권한 재검증 필요", "CHANGES_REQUESTED");
    assertEquals("CODEX", reload(task).get("nextAgent"));
    assertTrue(
        call(codex, "collaboration_read", Map.of("taskId", id)).toString().contains("권한 재검증 필요"));
    submit(codex, reload(task), "보완 제안", "INFO");
    submit(claude, reload(task), "검수 의견", "PASS");
    assertEquals("WAITING_APPROVAL", reload(task).get("status"));
    assertEquals(5, ((Number) reload(task).get("calls_used")).intValue());
    long before =
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_items WHERE approved_id IS NOT NULL", Long.class);
    assertEquals("APPROVED", change(reload(task), "approve").get("status"));
    assertEquals(
        before,
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_items WHERE approved_id IS NOT NULL", Long.class));
    assertTrue(
        connector.tools(claude.identity()).stream()
            .noneMatch(
                t -> t.get("name").toString().matches(".*(approve|delete|deploy|replace).*")));
  }

  @Test
  void retryAndConcurrentSubmissionsAppendOnlyOnce() throws Exception {
    var gpt = connection(owner, "GPT", true, true);
    var task = change(task(List.of("GPT"), 2), "start");
    var args =
        Map.<String, Object>of(
            "requestId",
            "retry-once",
            "taskId",
            task.get("id"),
            "version",
            task.get("version"),
            "text",
            "동일 요청 후보",
            "decision",
            "INFO");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var latch = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                latch.await();
                return call(gpt, "collaboration_submit", args);
              });
      var b =
          pool.submit(
              () -> {
                latch.await();
                return call(gpt, "collaboration_submit", args);
              });
      latch.countDown();
      assertEquals(
          a.get(10, TimeUnit.SECONDS).get("status"), b.get(10, TimeUnit.SECONDS).get("status"));
    }
    assertEquals(1, ((Number) reload(task).get("calls_used")).intValue());
    var changed = new HashMap<>(args);
    changed.put("text", "내용 변경");
    denied(409, () -> call(gpt, "collaboration_submit", changed));
  }

  @Test
  void pauseRevocationCapAndHumanReviewPersist() throws Exception {
    var codex = connection(owner, "CODEX", true, true);
    var claude = connection(owner, "CLAUDE", true, true);
    var task = change(task(List.of("CODEX", "CLAUDE"), 3), "start");
    var paused = change(task, "pause");
    denied(409, () -> submit(codex, paused, "pause bypass", "INFO"));
    change(paused, "resume");
    submit(codex, reload(task), "초안", "INFO");
    service.message(
        other,
        task.get("id").toString(),
        ((Number) reload(task).get("version")).longValue(),
        "인간 팀원 검수");
    submit(claude, reload(task), "보완", "CHANGES_REQUESTED");
    submit(codex, reload(task), "수정", "INFO");
    assertEquals("BLOCKED", reload(task).get("status"));
    denied(409, () -> change(reload(task), "resume"));
    spaces.member(owner, workspace, other.userId(), "REVOKE");
    denied(403, () -> service.detail(other, task.get("id").toString()));
    db.update(
        "DELETE FROM studio_members WHERE project_id='ai-agent-part1' AND user_id=?",
        owner.userId());
    denied(403, () -> call(codex, "collaboration_read", Map.of()));
  }

  @Test
  void projectIsolationMemoryAndSearch() throws Exception {
    denied(403, () -> spaces.list(stranger));
    String secret = spaces.create(other, "다른 제작 프로젝트").get("id").toString();
    assertTrue(spaces.list(owner).stream().noneMatch(w -> w.get("id").equals(secret)));
    denied(403, () -> spaces.snapshot(owner, secret));
    denied(403, () -> spaces.memory(viewer, workspace, "NOTE", "viewer edit", null));
    spaces.memory(other, workspace, "DECISION", "정본 SHA를 보존하기로 결정", null);
    assertEquals(1, spaces.search(viewer, workspace, "SHA").size());
    var viewerRead = connection(viewer, "GPT", false, false);
    assertTrue(call(viewerRead, "collaboration_read", Map.of()).toString().contains("정본 SHA"));
    denied(
        403,
        () ->
            connector.bind(
                owner,
                new CollaborationMcp.Bind(viewerRead.identity().grantId(), secret, "GPT", false)));
    denied(403, () -> spaces.member(other, workspace, viewer.userId(), "EDITOR"));
  }

  @Test
  void mcpHistoryPagesPreserveAllEventsWithoutLargeSingleResponses() throws Exception {
    var gpt = connection(owner, "GPT", true, true);
    var task = task(List.of("GPT"), 2);
    String id = task.get("id").toString();
    for (int i = 0; i < 24; i++)
      db.update(
          "INSERT INTO ai_collaboration_events(task_id,actor,event_type,body)"
              + " VALUES(?,'USER','QA','synthetic')",
          id);
    var first = call(gpt, "collaboration_read", Map.of("taskId", id));
    assertEquals(10, ((List<?>) first.get("events")).size());
    var second =
        call(
            gpt,
            "collaboration_read",
            Map.of("taskId", id, "beforeId", first.get("eventsNextBeforeId")));
    assertEquals(10, ((List<?>) second.get("events")).size());
    var third =
        call(
            gpt,
            "collaboration_read",
            Map.of("taskId", id, "beforeId", second.get("eventsNextBeforeId")));
    assertEquals(5, ((List<?>) third.get("events")).size());
  }

  @Test
  void connectorDocumentsPreserveActorShaVersionAndApprovedParent() throws Exception {
    var gpt = connection(owner, "GPT", true, true);
    var task = task(List.of("GPT"), 3);
    var draft =
        call(
            gpt,
            "collaboration_add_document",
            Map.of(
                "taskId",
                task.get("id"),
                "filename",
                "storyboard.md",
                "text",
                "# 합성 테스트 콘티\n실제 원본 아님"));
    String id = draft.get("id").toString();
    assertEquals("DRAFT", draft.get("status"));
    assertEquals("GPT", draft.get("registered_agent"));
    assertEquals(owner.userId(), ((Number) draft.get("registered_by")).longValue());
    assertFalse(draft.containsKey("storage_key"));
    assertEquals(CollaborationFiles.sha(files.bytes(owner, id)), draft.get("sha256"));
    denied(403, () -> files.review(other, id, "APPROVED"));
    files.review(owner, id, "APPROVED");
    var revision =
        call(
            gpt,
            "collaboration_add_document",
            Map.of(
                "taskId",
                task.get("id"),
                "filename",
                "storyboard-r2.md",
                "text",
                "# 수정 제안",
                "parentId",
                id));
    assertEquals("DRAFT", revision.get("status"));
    assertEquals(id, revision.get("parent_id"));
    assertEquals("APPROVED", files.metadata(owner, id).get("status"));
    assertTrue(files.text(viewer, id).toString().contains("실제 원본 아님"));
    var noOriginal = connection(owner, "CLAUDE", false, false);
    denied(403, () -> call(noOriginal, "collaboration_read", Map.of("fileId", id)));
  }

  @Test
  void binaryChunkUploadIsVerifiedBoundedIdempotentAndVisibleToTwoClients() throws Exception {
    var gpt = connection(owner, "GPT", true, true);
    var claude = connection(owner, "CLAUDE", false, true);
    var task = task(List.of("GPT"), 2);
    byte[] png =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aB9sAAAAASUVORK5CYII=");
    String upload =
        call(
                gpt,
                "collaboration_begin_upload",
                Map.of(
                    "taskId",
                    task.get("id"),
                    "filename",
                    "synthetic-test.png",
                    "size",
                    png.length,
                    "sha256",
                    CollaborationFiles.sha(png)))
            .get("uploadId")
            .toString();
    var chunk =
        Map.<String, Object>of(
            "uploadId", upload, "index", 0, "base64", Base64.getEncoder().encodeToString(png));
    call(gpt, "collaboration_upload_chunk", chunk);
    assertEquals(true, call(gpt, "collaboration_upload_chunk", chunk).get("duplicate"));
    denied(403, () -> call(claude, "collaboration_upload_chunk", chunk));
    denied(
        409,
        () ->
            call(
                gpt,
                "collaboration_upload_chunk",
                Map.of("uploadId", upload, "index", 0, "base64", "eA==")));
    var file = call(gpt, "collaboration_commit_upload", Map.of("uploadId", upload));
    assertEquals(
        file.get("id"),
        call(gpt, "collaboration_commit_upload", Map.of("uploadId", upload)).get("id"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_chunks WHERE upload_id=?",
            Integer.class,
            upload));
    Object a =
        connector.execute(
            gpt.identity(),
            "collaboration_read",
            json.valueToTree(Map.of("fileId", file.get("id"))));
    Object b =
        connector.execute(
            claude.identity(),
            "collaboration_read",
            json.valueToTree(Map.of("fileId", file.get("id"))));
    assertEquals(a, b);
    var image = (CollaborationMcp.Content) a;
    assertEquals("image", image.blocks().get(1).get("type"));
    assertEquals(
        file.get("sha256"),
        CollaborationFiles.sha(
            Base64.getDecoder().decode(image.blocks().get(1).get("data").toString())));
    String invalid =
        call(
                gpt,
                "collaboration_begin_upload",
                Map.of(
                    "taskId",
                    task.get("id"),
                    "filename",
                    "bad.md",
                    "size",
                    1,
                    "sha256",
                    "0".repeat(64)))
            .get("uploadId")
            .toString();
    call(
        gpt,
        "collaboration_upload_chunk",
        Map.of("uploadId", invalid, "index", 0, "base64", "eA=="));
    denied(422, () -> call(gpt, "collaboration_commit_upload", Map.of("uploadId", invalid)));
  }

  @Test
  void rollbackDeletesOnlyNewBlobAndPreservesDeduplicatedOriginal() throws Exception {
    var gpt = connection(owner, "GPT", true, true);
    var task = task(List.of("GPT"), 2);
    String id = task.get("id").toString();
    var original =
        files.document(
            owner,
            gpt.identity().grantId(),
            workspace,
            "GPT",
            id,
            "original.md",
            "preserved",
            null);
    files.review(owner, original.get("id").toString(), "APPROVED");
    var template = new TransactionTemplate(manager);
    assertThrows(
        IllegalStateException.class,
        () ->
            template.execute(
                t -> {
                  files.document(
                      owner,
                      gpt.identity().grantId(),
                      workspace,
                      "GPT",
                      id,
                      "dedup.md",
                      "preserved",
                      null);
                  throw new IllegalStateException("rollback");
                }));
    assertEquals(
        "preserved",
        new String(files.bytes(owner, original.get("id").toString()), StandardCharsets.UTF_8));
    assertEquals("APPROVED", files.metadata(owner, original.get("id").toString()).get("status"));
    long before;
    try (var paths = Files.walk(Path.of("/tmp/f2r-collaboration-test-files"))) {
      before = paths.filter(Files::isRegularFile).count();
    }
    assertThrows(
        IllegalStateException.class,
        () ->
            template.execute(
                t -> {
                  files.document(
                      owner,
                      gpt.identity().grantId(),
                      workspace,
                      "GPT",
                      id,
                      "new.md",
                      UUID.randomUUID().toString(),
                      null);
                  throw new IllegalStateException("rollback");
                }));
    try (var paths = Files.walk(Path.of("/tmp/f2r-collaboration-test-files"))) {
      assertEquals(before, paths.filter(Files::isRegularFile).count());
    }
  }

  HttpResponse<String> request(
      String path, String method, String body, String origin, AuthenticatedPrincipal p)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:19088" + path))
            .timeout(Duration.ofSeconds(15));
    if (p != null)
      b.header(
          "Cookie",
          "ACCESS_TOKEN=" + jwt.issue(p.userId(), p.loginId(), p.role(), p.credentialStamp()));
    if (origin != null) b.header("Origin", origin);
    if (method.equals("POST"))
      b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void actualHttpProtectsOriginBodySizeAndExportsHumanHistory() throws Exception {
    assertEquals(401, request("/api/collaboration/tasks", "GET", null, null, null).statusCode());
    assertEquals(
        403,
        request("/api/collaboration/tasks", "POST", "{}", "https://evil.invalid", owner)
            .statusCode());
    var task = task(List.of("GPT"), 2);
    var response =
        request("/api/collaboration/tasks/" + task.get("id") + "/export", "GET", null, null, owner);
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("합성 QA"));
    var chunked =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:19088/api/collaboration/tasks"))
            .header("Content-Type", "application/json")
            .header("Origin", "http://127.0.0.1:19088")
            .header(
                "Cookie",
                "ACCESS_TOKEN="
                    + jwt.issue(
                        owner.userId(), owner.loginId(), owner.role(), owner.credentialStamp()))
            .POST(
                HttpRequest.BodyPublishers.ofInputStream(
                    () -> new java.io.ByteArrayInputStream(new byte[65537])))
            .build();
    assertEquals(
        413,
        HttpClient.newHttpClient()
            .send(chunked, HttpResponse.BodyHandlers.ofString())
            .statusCode());
  }

  @Test
  void actualHttpMcpUsesOAuthNotSessionCookiesAndContainsNoApprovalTool() throws Exception {
    var c = connection(owner, "GPT", true, true);
    var req =
        HttpRequest.newBuilder(URI.create(oauth.resource()))
            .header("Authorization", "Bearer " + c.token())
            .header("Content-Type", "application/json")
            .header("MCP-Protocol-Version", "2025-11-25")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
            .build();
    var result = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, result.statusCode());
    var body = json.readTree(result.body());
    assertTrue(body.path("result").path("tools").toString().contains("collaboration_add_document"));
    assertFalse(body.path("result").path("tools").toString().contains("collaboration_approve"));
  }

  @Test
  void browserAndOfficialSdkWhenExplicitlyEnabled() throws Exception {
    if (!"true".equals(System.getenv("F2R_COLLAB_BROWSER_TEST"))) return;
    var gpt = connection(owner, "GPT", true, true);
    var codex = connection(owner, "CODEX", true, true);
    var claude = connection(owner, "CLAUDE", true, true);
    Path path = Path.of("/tmp/f2r-collab-browser.json");
    var fixture =
        Map.of(
            "cookie",
            jwt.issue(owner.userId(), owner.loginId(), owner.role(), owner.credentialStamp()),
            "workspace",
            workspace,
            "gpt",
            gpt.token(),
            "codex",
            codex.token(),
            "claude",
            claude.token());
    Files.createFile(
        path,
        java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
    Files.writeString(path, json.writeValueAsString(fixture));
    try {
      var process =
          new ProcessBuilder("node", "scripts/ai-agent-studio/collaboration-qa.cjs")
              .inheritIO()
              .start();
      assertTrue(process.waitFor(120, TimeUnit.SECONDS));
      assertEquals(0, process.exitValue());
      if ("true".equals(System.getenv("F2R_COLLAB_SDK_TEST"))) {
        var sdk =
            new ProcessBuilder("python3", "scripts/ai-agent-studio/collaboration-sdk-qa.py")
                .inheritIO()
                .start();
        assertTrue(sdk.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, sdk.exitValue());
      }
    } finally {
      Files.deleteIfExists(path);
    }
  }
}
