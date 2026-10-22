package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = {
      "server.port=19088",
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3319/f2r_cluster_test",
      "spring.datasource.username=f2r_test",
      "spring.datasource.password=isolated_cluster_password",
      "spring.flyway.locations=classpath:db/migration,classpath:db/dev-seed",
      "spring.jpa.show-sql=false",
      "logging.level.org.hibernate.SQL=WARN",
      "app.file.upload-dir=/tmp/f2r-collaboration-test-files",
      "app.collaboration.public-base-url=http://127.0.0.1:19088",
      "app.collaboration.daily-budget-microusd=1000000"
    })
@EnabledIfEnvironmentVariable(named = "F2R_COLLAB_MYSQL_TEST", matches = "true")
class CollaborationMySqlTest {
  @Autowired CollaborationService service;
  @Autowired CollaborationRunner runner;
  @Autowired StudioService studio;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate db;
  @Autowired JwtTokenProvider jwt;
  @Autowired f2r.tutor_solution.private_tutor_solution_back.domain.mcp.McpCatalog catalog;
  @MockitoBean CollaborationProvider provider;
  AuthenticatedPrincipal owner, other, viewer, stranger;

  AuthenticatedPrincipal user(String kind, Role role) {
    String login = "collab-" + kind + "-" + UUID.randomUUID().toString().substring(0, 12);
    var user =
        users.saveAndFlush(
            new User(login, "synthetic-test-password", "격리 검증", login, null, null, role));
    return new AuthenticatedPrincipal(user.getUserId(), login, role.name());
  }

  @BeforeEach
  void setup() throws Exception {
    var admin = user("admin", Role.ADMIN);
    studio.initialize(admin);
    owner = user("owner", Role.TUTOR);
    other = user("other", Role.TUTOR);
    viewer = user("viewer", Role.TUTOR);
    stranger = user("stranger", Role.TUTOR);
    studio.member(admin, owner.userId(), "EDITOR");
    studio.member(admin, other.userId(), "EDITOR");
    studio.member(admin, viewer.userId(), "VIEWER");
    db.update("DELETE FROM ai_collaboration_budget_days");
    when(provider.readiness(anyString()))
        .thenReturn(new CollaborationProvider.Ready(true, "SYNTHETIC_TEST_ONLY"));
    when(provider.reserveMicrousd(anyString(), anyString())).thenReturn(100L);
    when(provider.complete(anyString(), anyString()))
        .thenAnswer(
            inv ->
                new CollaborationProvider.Reply(
                    "합성 격리 검증 제안 — 실제 API 호출 아님",
                    inv.getArgument(0).equals("CLAUDE") ? "PASS" : "INFO"));
  }

  Map<String, Object> task(List<String> agents, int cap, long budget) {
    return service.create(
        owner,
        new CollaborationService.Create(
            "격리 협업 검증", "실제 작품이 아닌 합성 테스트. 정본/운영 데이터 변경 금지.", agents, cap, budget));
  }

  Map<String, Object> change(Map<String, Object> task, String action) {
    return service.change(
        owner,
        task.get("id").toString(),
        new CollaborationService.Change(
            ((Number) task.get("version")).longValue(), action, true, true));
  }

  void run(Map<String, Object> task) {
    change(task, "start");
    runner.run(task.get("id").toString());
  }

  Map<String, Object> reload(Map<String, Object> task) {
    return service.detail(owner, task.get("id").toString());
  }

  @Test
  void selectedAgentsExchangeMessagesAndClaudeRequestsCodexCorrectionThenRereview()
      throws Exception {
    var calls = new ArrayList<String>();
    var reviews = new java.util.concurrent.atomic.AtomicInteger();
    when(provider.complete(anyString(), anyString()))
        .thenAnswer(
            inv -> {
              String agent = inv.getArgument(0), prompt = inv.getArgument(1);
              calls.add(agent);
              if (agent.equals("CLAUDE") && reviews.getAndIncrement() == 0)
                return new CollaborationProvider.Reply("fix required", "CHANGES_REQUESTED");
              if (calls.size() == 4) assertTrue(prompt.contains("fix required"));
              return new CollaborationProvider.Reply(
                  "synthetic proposal", agent.equals("CLAUDE") ? "PASS" : "INFO");
            });
    var task = task(List.of("GPT", "CODEX", "CLAUDE"), 8, 10000);
    run(task);
    var done = reload(task);
    assertEquals(List.of("GPT", "CODEX", "CLAUDE", "CODEX", "CLAUDE"), calls);
    assertEquals("WAITING_APPROVAL", done.get("status"));
    assertEquals(5, ((Number) done.get("calls_used")).intValue());
    verify(provider, never()).complete(eq("MUSE"), anyString());
    assertEquals("APPROVED", change(done, "approve").get("status"));
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.message(
                owner,
                task.get("id").toString(),
                ((Number) reload(task).get("version")).longValue(),
                "overwrite"));
  }

  @Test
  void noImplicitReviewerOrMuseAndManualApprovalIsSeparateFromCanon() {
    long approvedBefore =
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_items WHERE approved_id IS NOT NULL", Long.class);
    var task = task(List.of("GPT"), 2, 1000);
    run(task);
    assertEquals("WAITING_APPROVAL", reload(task).get("status"));
    change(reload(task), "approve");
    assertEquals(
        approvedBefore,
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_items WHERE approved_id IS NOT NULL", Long.class));
  }

  @Test
  void permissionOwnershipVersionAndConsentCannotBeBypassed() {
    var task = task(List.of("GPT"), 2, 1000);
    String id = task.get("id").toString();
    assertThrows(ResponseStatusException.class, () -> service.detail(stranger, id));
    assertNotNull(service.detail(viewer, id));
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.create(
                viewer, new CollaborationService.Create("x", "y", List.of("GPT"), 1, 1)));
    assertThrows(
        ResponseStatusException.class,
        () -> service.change(other, id, new CollaborationService.Change(0, "start", true, false)));
    assertThrows(
        ResponseStatusException.class,
        () -> service.change(owner, id, new CollaborationService.Change(0, "start", false, false)));
    service.message(owner, id, 0, "new instruction");
    assertThrows(ResponseStatusException.class, () -> change(task, "start"));
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.create(
                owner, new CollaborationService.Create("x", "y", List.of("GPT", "GPT"), 1, 1)));
  }

  @Test
  void callAndCostCapsSurviveNewRunnerInstances() throws Exception {
    when(provider.complete(anyString(), anyString()))
        .thenReturn(new CollaborationProvider.Reply("changes", "CHANGES_REQUESTED"));
    var task = task(List.of("CODEX", "CLAUDE"), 3, 10000);
    run(task);
    assertEquals("BLOCKED", reload(task).get("status"));
    assertEquals(3, ((Number) reload(task).get("calls_used")).intValue());
    change(reload(task), "resume");
    new CollaborationRunner(service, provider).run(task.get("id").toString());
    assertEquals(3, ((Number) reload(task).get("calls_used")).intValue());
    var cost = task(List.of("GPT"), 2, 99);
    run(cost);
    assertEquals("BLOCKED", reload(cost).get("status"));
    assertEquals(0, ((Number) reload(cost).get("calls_used")).intValue());
  }

  @Test
  void onlyOneDispatcherCanClaimTaskAndDailyBudgetIsAtomicAcrossTasks() throws Exception {
    var a = task(List.of("GPT"), 2, 1000000);
    var b = task(List.of("GPT"), 2, 1000000);
    change(a, "start");
    change(b, "start");
    db.update("UPDATE ai_collaboration_budget_days SET reserved_microusd=0");
    db.update("INSERT IGNORE INTO ai_collaboration_budget_days VALUES(UTC_DATE(),999900)");
    db.update("UPDATE ai_collaboration_budget_days SET reserved_microusd=999900");
    try (var pool = Executors.newFixedThreadPool(3)) {
      var ready = new CountDownLatch(1);
      var fa =
          pool.submit(
              () -> {
                ready.await();
                return service.claim(a.get("id").toString());
              });
      var fb =
          pool.submit(
              () -> {
                ready.await();
                return service.claim(b.get("id").toString());
              });
      ready.countDown();
      var ca = fa.get(10, TimeUnit.SECONDS);
      var cb = fb.get(10, TimeUnit.SECONDS);
      assertEquals(1, (ca == null ? 0 : 1) + (cb == null ? 0 : 1));
      String winner = ca != null ? ca.taskId() : cb.taskId();
      assertNull(service.claim(winner));
      assertEquals(
          1000000L,
          db.queryForObject(
              "SELECT reserved_microusd FROM ai_collaboration_budget_days WHERE"
                  + " budget_day=UTC_DATE()",
              Long.class));
    }
  }

  @Test
  void pauseDuringCallStopsNextAgentAndResumeContinuesWithoutUnselectedAi() throws Exception {
    var task = task(List.of("GPT", "CODEX"), 4, 1000);
    when(provider.complete(eq("GPT"), anyString()))
        .thenAnswer(
            inv -> {
              change(reload(task), "pause");
              return new CollaborationProvider.Reply("planning done", "INFO");
            });
    run(task);
    assertEquals("PAUSED", reload(task).get("status"));
    assertEquals(1, ((Number) reload(task).get("calls_used")).intValue());
    change(reload(task), "resume");
    runner.run(task.get("id").toString());
    assertEquals("WAITING_APPROVAL", reload(task).get("status"));
    verify(provider, times(1)).complete(eq("GPT"), anyString());
    verify(provider, times(1)).complete(eq("CODEX"), anyString());
  }

  @Test
  void ambiguousFailureKeepsReservationAndRequiresExplicitAcknowledgment() throws Exception {
    var task = task(List.of("GPT"), 2, 1000);
    when(provider.complete(anyString(), anyString()))
        .thenThrow(new java.io.IOException("SECRET_CANARY"));
    run(task);
    var failed = reload(task);
    assertEquals("UNKNOWN", failed.get("status"));
    assertEquals(100L, ((Number) failed.get("reserved_microusd")).longValue());
    assertFalse(failed.get("events").toString().contains("SECRET_CANARY"));
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.change(
                owner,
                task.get("id").toString(),
                new CollaborationService.Change(
                    ((Number) failed.get("version")).longValue(), "resume", true, false)));
  }

  @Test
  void studioMembershipRevocationPreventsDispatchAndReading() {
    var task = task(List.of("GPT"), 2, 1000);
    change(task, "start");
    db.update(
        "DELETE FROM studio_members WHERE project_id=? AND user_id=?",
        "ai-agent-part1",
        owner.userId());
    assertNull(service.claim(task.get("id").toString()));
    assertThrows(ResponseStatusException.class, () -> reload(task));
  }

  HttpResponse<String> request(
      String path, String method, String body, String origin, AuthenticatedPrincipal p)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:19088" + path))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json");
    if (p != null)
      builder.header(
          "Cookie",
          "ACCESS_TOKEN=" + jwt.issue(p.userId(), p.loginId(), p.role(), p.credentialStamp()));
    if (origin != null) builder.header("Origin", origin);
    if (method.equals("POST"))
      builder
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body));
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void realHttpEnforcesAuthenticationOriginAndExportsReadOnlyHistory() throws Exception {
    assertEquals(401, request("/api/collaboration/tasks", "GET", null, null, null).statusCode());
    assertEquals(
        403,
        request("/api/collaboration/tasks", "POST", "{}", "https://evil.invalid", owner)
            .statusCode());
    var task = task(List.of("GPT"), 2, 0);
    var export =
        request("/api/collaboration/tasks/" + task.get("id") + "/export", "GET", null, null, owner);
    assertEquals(200, export.statusCode());
    assertTrue(
        export.headers().firstValue("content-disposition").orElse("").contains("attachment"));
    assertTrue(export.body().contains("실제 작품이 아닌 합성 테스트"));
  }

  @Test
  void chunkedOversizedBodyDeniedBeforeJsonParsingAndMcpHasNoWriteTools() throws Exception {
    var request =
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
            .send(request, HttpResponse.BodyHandlers.ofString())
            .statusCode());
    var routes =
        catalog.inventory().stream()
            .filter(row -> row.get("path").toString().startsWith("/api/collaboration/"))
            .toList();
    assertTrue(
        routes.stream()
            .anyMatch(
                row ->
                    row.get("id").equals("GET /api/collaboration/tasks")
                        && Boolean.TRUE.equals(row.get("readable"))));
    assertTrue(
        routes.stream()
            .filter(row -> !row.get("id").toString().startsWith("GET "))
            .noneMatch(row -> Boolean.TRUE.equals(row.get("readable"))));
  }

  @Test
  void recoveryPreservesAmbiguousBudgetAndHistoryPaginationHasNoGaps() {
    var task = task(List.of("GPT"), 3, 1000);
    change(task, "start");
    var call = service.claim(task.get("id").toString());
    assertNotNull(call);
    assertThrows(ResponseStatusException.class, () -> change(reload(task), "recover"));
    db.update(
        "UPDATE ai_collaboration_tasks SET updated_at=CURRENT_TIMESTAMP - INTERVAL 11 MINUTE WHERE"
            + " id=?",
        call.taskId());
    assertEquals("UNKNOWN", change(reload(task), "recover").get("status"));
    assertEquals(100L, ((Number) reload(task).get("reserved_microusd")).longValue());
    service.finish(call, new CollaborationProvider.Reply("late obsolete callback", "INFO"));
    assertFalse(reload(task).get("events").toString().contains("late obsolete callback"));
    for (int i = 0; i < 205; i++)
      db.update(
          "INSERT INTO ai_collaboration_events(task_id,actor,event_type,body)"
              + " VALUES(?,'USER','TEST','synthetic')",
          call.taskId());
    var first = service.events(owner, call.taskId(), 0);
    assertEquals(200, first.size());
    long last = ((Number) first.getLast().get("id")).longValue();
    var second = service.events(owner, call.taskId(), last);
    assertFalse(second.isEmpty());
    assertTrue(second.stream().allMatch(e -> ((Number) e.get("id")).longValue() < last));
  }

  @Test
  void browserQaWhenExplicitlyEnabled() throws Exception {
    if (!"true".equals(System.getenv("F2R_COLLAB_BROWSER_TEST"))) return;
    var fixture =
        Map.of(
            "cookie",
            jwt.issue(owner.userId(), owner.loginId(), owner.role(), owner.credentialStamp()));
    Path path = Path.of("/tmp/f2r-collab-browser.json");
    Files.writeString(
        path, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fixture));
    Files.setPosixFilePermissions(
        path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    var process =
        new ProcessBuilder("node", "scripts/ai-agent-studio/collaboration-qa.cjs")
            .inheritIO()
            .start();
    assertTrue(process.waitFor(90, TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
    Files.deleteIfExists(path);
  }
}
