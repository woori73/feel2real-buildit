package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioAiService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Claude 업로드 → ChatGPT 조회·검수·수정 요청 → Claude 재등록 → ChatGPT 비교·판정 → 사람 승인 흐름을 실제 /mcp HTTP와 토큰
 * 업로드 API로 격리 MySQL에서 검증한다. 두 연결은 공식 callback으로 등록한 시험 클라이언트이며 실제 ChatGPT·Claude 제품 계정이
 * 아니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = {
      "server.port=18088",
      "app.mcp.enabled=true",
      "app.studio.access.operators-only=false",
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
class McpStudioCollaborationMySqlTest {
  @Autowired StudioService studio;
  @Autowired StudioAiService ai;
  @Autowired McpOAuth oauth;
  @Autowired UserRepository users;
  @Autowired AdminSessionValidator stamps;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  static JwtTokenProvider.AuthenticatedPrincipal director, viewer;
  static String claude, chatgpt, readOnly, workId, v1, v2, reviewId, taskId, v1Sha;
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
                            .name("격리 협업 테스트")
                            .nickname(login)
                            .role(role)
                            .build()));
    return new JwtTokenProvider.AuthenticatedPrincipal(
        u.getUserId(), u.getLoginId(), u.getRole().name(), stamps.stampFor(u.getUserId()));
  }

  String connect(String name, String callback, Set<String> scopes) {
    var c = oauth.register(Map.of("client_name", name, "redirect_uris", List.of(callback)));
    String client = (String) c.get("client_id"), verifier = "b".repeat(43);
    var q =
        Map.of(
            "client_id", client,
            "redirect_uri", callback,
            "response_type", "code",
            "state", "collab-state",
            "code_challenge_method", "S256",
            "code_challenge", McpOAuth.challenge(verifier),
            "resource", base + "/mcp");
    String redirect = oauth.authorize(director, oauth.pending(director, q), scopes, true);
    String code = redirect.substring(redirect.indexOf("code=") + 5, redirect.indexOf("&state="));
    return (String)
        oauth
            .token(
                Map.of(
                    "grant_type", "authorization_code",
                    "client_id", client,
                    "redirect_uri", callback,
                    "code", code,
                    "code_verifier", verifier,
                    "resource", base + "/mcp"))
            .get("access_token");
  }

  HttpResponse<String> post(String body, String token) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(base + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  JsonNode call(String token, String tool, Map<String, ?> args) throws Exception {
    var r =
        post(
            json.writeValueAsString(
                Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "tools/call",
                    "params", Map.of("name", tool, "arguments", args))),
            token);
    assertEquals(200, r.statusCode(), r.body());
    return json.readTree(r.body()).get("result");
  }

  /** 성공한 도구 결과의 JSON 본문. */
  JsonNode ok(String token, String tool, Map<String, ?> args) throws Exception {
    var result = call(token, tool, args);
    assertFalse(result.get("isError").asBoolean(), tool + " → " + result);
    return json.readTree(result.get("content").get(0).get("text").asText());
  }

  String denied(String token, String tool, Map<String, ?> args) throws Exception {
    var result = call(token, tool, args);
    assertTrue(result.get("isError").asBoolean(), tool + " should fail → " + result);
    return result.get("content").get(0).get("text").asText();
  }

  static byte[] mp4(String marker) {
    var out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] {0, 0, 0, 24});
    out.writeBytes("ftypisom".getBytes(StandardCharsets.ISO_8859_1));
    out.writeBytes(new byte[12]);
    out.writeBytes(("SYNTHETIC ROUGH CUT " + marker).getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }

  HttpResponse<String> tokenUpload(String token, Map<String, String> fields, String name, byte[] file)
      throws Exception {
    String boundary = "----f2rstudio" + UUID.randomUUID();
    var body = new ByteArrayOutputStream();
    for (var e : fields.entrySet())
      body.writeBytes(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\""
                  + e.getKey()
                  + "\"\r\n\r\n"
                  + e.getValue()
                  + "\r\n")
              .getBytes(StandardCharsets.UTF_8));
    body.writeBytes(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.writeBytes(file);
    body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return http.send(
        HttpRequest.newBuilder(URI.create(base + "/mcp/studio/files"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @Order(1)
  void givenProject_whenClaudeUploadsRoughCutWithToken_thenServerVerifiedNewVersion()
      throws Exception {
    director = user("studio-collab-director", Role.ADMIN);
    viewer = user("studio-collab-viewer", Role.STUDENT);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM studio_projects WHERE id=?", Long.class, StudioService.PROJECT)
        == 0) studio.initialize(director);
    studio.member(director, viewer.userId(), "VIEWER");
    claude =
        connect(
            "Claude protocol test",
            "https://claude.ai/api/mcp/auth_callback",
            Set.of("studio:read", "studio:propose"));
    chatgpt =
        connect(
            "ChatGPT protocol test",
            "https://chatgpt.com/connector/oauth/isolated-test-callback",
            Set.of("studio:read", "studio:propose", "originals:read"));
    readOnly =
        connect(
            "ChatGPT read only test",
            "https://chatgpt.com/connector/oauth/isolated-test-callback",
            Set.of("studio:read"));
    workId = "EP01-S03-C" + (100 + new Random().nextInt(800));
    var response =
        tokenUpload(
            claude,
            Map.of(
                "workId", workId,
                "versionLabel", "v1",
                "title", "EP01 S03 러프 영상",
                "kind", "VIDEO",
                "episode", "1",
                "changeNote", "Claude 러프 v1 (합성 시험 파일)"),
            "ep01-s03-rough-v1.mp4",
            mp4("v1"));
    assertEquals(200, response.statusCode(), response.body());
    var body = json.readTree(response.body());
    assertTrue(body.get("serverVerified").asBoolean());
    assertEquals("v1", body.get("version").asText());
    assertEquals("UNREVIEWED", body.get("reviewStatus").asText());
    v1 = body.get("revisionId").asText();
    v1Sha = body.get("file").get("sha256").asText();
    assertEquals(
        HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mp4("v1"))),
        v1Sha);
    var stored = studio.revision(director, v1);
    assertEquals("AI_PROPOSAL", stored.get("origin"));
    assertNull(stored.get("approved_id"));
    // 확장자와 실제 형식이 다르면 등록하지 않는다.
    var spoof =
        tokenUpload(
            claude,
            Map.of("workId", workId, "versionLabel", "bad", "changeNote", "형식 위조"),
            "fake.mp4",
            "not a video".getBytes(StandardCharsets.UTF_8));
    assertEquals(400, spoof.statusCode(), spoof.body());
    // 쓰기 동의 없는 토큰은 업로드 API도 거부한다.
    assertEquals(
        403,
        tokenUpload(readOnly, Map.of("workId", workId, "versionLabel", "x", "changeNote", "x"), "a.mp4", mp4("x"))
            .statusCode());
  }

  @Test
  @Order(2)
  void givenChatGpt_whenListingReadingAndReviewing_thenReviewAndTaskRecordedWithoutApproval()
      throws Exception {
    var listed = ok(chatgpt, "list_studio_assets", Map.of("workId", workId));
    var item = listed.get("items").get(0);
    assertEquals(v1, item.get("revisionId").asText());
    assertEquals(v1Sha, item.get("file").get("sha256").asText());
    assertTrue(item.get("file").get("url").asText().contains("/api/studio/files/"));
    var read = ok(chatgpt, "read_studio_asset", Map.of("revisionId", v1));
    assertEquals(workId, read.get("workId").asText());
    assertEquals("mp4", read.get("file").get("format").asText());

    var issues =
        List.of(
            Map.of("severity", "HIGH", "description", "주인공 얼굴이 기준 시트와 다름", "timecode", "00:12"),
            Map.of("severity", "MEDIUM", "description", "컷 전환이 급함", "timecode", "00:31", "cutNumber", 24),
            Map.of("severity", "LOW", "description", "자막 위치", "suggestion", "하단 10% 안쪽"));
    var reviewArgs =
        Map.of(
            "revisionId", v1,
            "verdict", "NEEDS_CHANGES",
            "summary", "인물 일관성 문제로 수정 필요",
            "issues", issues,
            "idempotencyKey", "review-" + workId);
    var review = ok(chatgpt, "submit_studio_review", reviewArgs);
    reviewId = review.get("reviewId").asText();
    assertEquals("REVIEWED_NEEDS_CHANGES", review.get("reviewStatus").asText());
    // 같은 키 재전송은 같은 결과, 다른 내용은 409.
    var replay = ok(chatgpt, "submit_studio_review", reviewArgs);
    assertEquals(reviewId, replay.get("reviewId").asText());
    assertTrue(replay.get("replayed").asBoolean());
    var changed = new LinkedHashMap<String, Object>(reviewArgs);
    changed.put("summary", "다른 내용");
    denied(chatgpt, "submit_studio_review", changed);
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_reviews WHERE item_key=?", Long.class, workId));
    var stored = db.queryForMap("SELECT * FROM studio_reviews WHERE id=?", reviewId);
    assertEquals("AI_CONNECTOR", stored.get("author_type"));
    assertEquals("ChatGPT protocol test", stored.get("ai_client"));
    assertNull(studio.revision(director, v1).get("approved_id"), "검수는 승인이 아니다");

    var task =
        ok(
            chatgpt,
            "create_studio_task",
            Map.of(
                "workId", workId,
                "revisionId", v1,
                "reviewId", reviewId,
                "title", "얼굴 일관성·컷 전환 수정",
                "instructions", "검수 보고서 3건 반영 후 v2 등록",
                "assignee", "CLAUDE"));
    taskId = task.get("taskId").asText();
    assertEquals("OPEN", task.get("status").asText());

    // 읽기 전용 연결은 쓰기 도구를 쓸 수 없다.
    assertTrue(
        denied(readOnly, "submit_studio_review", Map.of("revisionId", v1, "verdict", "PASS", "summary", "x"))
            .contains("studio_propose_scope_required"));
    assertTrue(
        denied(readOnly, "create_studio_task", Map.of("workId", workId, "title", "x", "instructions", "x"))
            .contains("studio_propose_scope_required"));
  }

  @Test
  @Order(3)
  void givenClaude_whenTaskReadAndV2Uploaded_thenNewVersionWithoutOverwrite() throws Exception {
    var tasks = ok(claude, "list_studio_tasks", Map.of("assignee", "CLAUDE", "workId", workId));
    assertEquals(taskId, tasks.get(0).get("taskId").asText());
    var versions = ok(claude, "get_asset_versions", Map.of("workId", workId));
    assertEquals(reviewId, versions.get("reviews").get(0).get("id").asText());
    assertEquals(3, versions.get("reviews").get(0).get("issues").size());
    ok(claude, "update_studio_task", Map.of("taskId", taskId, "status", "IN_PROGRESS"));

    String v2Bytes = Base64.getEncoder().encodeToString(mp4("v2"));
    // 오래된 head 기준 등록은 거부된다.
    denied(
        claude,
        "upload_studio_asset",
        Map.of(
            "workId", workId,
            "expectedHeadRevisionId", UUID.randomUUID().toString(),
            "versionLabel", "v2",
            "filename", "ep01-s03-rough-v2.mp4",
            "contentBase64", v2Bytes,
            "changeNote", "stale"));
    var uploadArgs =
        Map.of(
            "workId", workId,
            "expectedHeadRevisionId", v1,
            "versionLabel", "v2",
            "filename", "ep01-s03-rough-v2.mp4",
            "contentBase64", v2Bytes,
            "changeNote", "얼굴 기준 시트 반영, 00:31 전환 완화",
            "taskId", taskId);
    var uploaded = ok(claude, "upload_studio_asset", uploadArgs);
    v2 = uploaded.get("revisionId").asText();
    assertNotEquals(v1Sha, uploaded.get("file").get("sha256").asText());
    assertTrue(ok(claude, "upload_studio_asset", uploadArgs).get("replayed").asBoolean());
    ok(claude, "update_studio_task", Map.of("taskId", taskId, "status", "DONE", "resultRevisionId", v2));
    // v1 원본·버전은 그대로 남는다.
    assertEquals(v1, studio.revision(director, v2).get("parent_id"));
    var v1File = (String) studio.revision(director, v1).get("file_id");
    assertArrayEquals(mp4("v1"), studio.bytes(director, v1File));
    assertTrue(
        denied(claude, "update_studio_task", Map.of("taskId", taskId, "status", "IN_PROGRESS"))
            .contains("종료된 작업"));
  }

  @Test
  @Order(4)
  void givenChatGpt_whenComparingVersions_thenPassReviewButOnlyHumanApproves() throws Exception {
    var versions = ok(chatgpt, "get_asset_versions", Map.of("workId", workId));
    assertEquals(2, versions.get("versions").size());
    assertEquals(v2, versions.get("headRevisionId").asText());
    assertEquals("DONE", versions.get("tasks").get(0).get("status").asText());
    var pass =
        ok(
            chatgpt,
            "submit_studio_review",
            Map.of("revisionId", v2, "verdict", "PASS", "summary", "v1 대비 3건 모두 개선"));
    assertEquals("REVIEWED_PASS", pass.get("reviewStatus").asText());
    assertNull(studio.revision(director, v2).get("approved_id"));

    // 승인 도구는 MCP에 없다. 사람 감독만 승인한다.
    var listed =
        json.readTree(
            post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", chatgpt).body());
    for (var tool : listed.get("result").get("tools")) {
      String name = tool.get("name").asText();
      assertFalse(name.matches(".*(approve|delete|restore|publish|deploy).*"), name);
      assertFalse(tool.get("annotations").get("destructiveHint").asBoolean(), name);
    }
    studio.review(director, v2, "APPROVE", "감독 승인");
    var approved = ok(chatgpt, "list_studio_assets", Map.of("workId", workId)).get("items").get(0);
    assertEquals("APPROVED", approved.get("reviewStatus").asText());
    assertEquals(v2, approved.get("approvedRevisionId").asText());
  }

  @Test
  @Order(5)
  void givenLimitsAndRoles_whenViolated_thenDenied() throws Exception {
    // 쓰기 동의가 없는 토큰은 64 KiB를 넘는 본문을 보낼 수 없다.
    String big =
        json.writeValueAsString(
            Map.of(
                "jsonrpc", "2.0",
                "id", 3,
                "method", "tools/call",
                "params",
                Map.of(
                    "name",
                    "upload_studio_asset",
                    "arguments",
                    Map.of("contentBase64", "A".repeat(70000)))));
    assertEquals(413, post(big, readOnly).statusCode());
    // 열람(VIEWER) 회원은 검수·작업·등록 권한이 없다. 연결 동의가 프로젝트 역할을 대신하지 않는다.
    var forbidden =
        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () ->
                ai.submitReview(
                    viewer,
                    StudioAiService.Connector.HUMAN,
                    v2,
                    "COMMENT",
                    "열람자 의견",
                    List.of(),
                    null));
    assertEquals(403, forbidden.getStatusCode().value());
    assertFalse(ai.listAssets(viewer, null, null, null, workId, 10, 0).isEmpty());
    // 검수 보고서는 승인 포인터를 바꾸지 않는다. 승인 후 AI의 새 버전도 승인본을 덮어쓰지 않는다.
    var v3 =
        ok(
            claude,
            "upload_studio_asset",
            Map.of(
                "workId", workId,
                "expectedHeadRevisionId", v2,
                "versionLabel", "v3",
                "filename", "ep01-s03-rough-v3.mp4",
                "contentBase64", Base64.getEncoder().encodeToString(mp4("v3")),
                "changeNote", "승인 뒤 추가 제안"));
    assertEquals("UNREVIEWED", v3.get("reviewStatus").asText());
    assertEquals(v2, studio.revision(director, v3.get("revisionId").asText()).get("approved_id"));
  }

  @AfterAll
  static void keepRows() {
    // 다른 MySQL 테스트가 같은 schema를 초기화하므로 여기서는 지우지 않는다. 결과 행은 재현 근거로 남는다.
  }
}
