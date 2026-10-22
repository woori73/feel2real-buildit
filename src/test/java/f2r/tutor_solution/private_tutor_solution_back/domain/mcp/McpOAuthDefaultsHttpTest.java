package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * 앱의 기본 redirect URI 허용 목록(오버라이드 없음)으로 실제 HTTP OAuth 동의 흐름을 검증한다.
 * 실제 ChatGPT·Claude 접속이 아니라, 공식 문서의 콜백 형태가 서버 정책을 통과하는지의 시험이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = {
      "server.port=18089",
      "app.mcp.enabled=true",
      "app.mcp.public-base-url=http://127.0.0.1:18089",
      "app.mcp.internal-base-url=http://127.0.0.1:18089",
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3318/f2r_studio_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci",
      "spring.datasource.username=f2r_test",
      "spring.datasource.password=isolated_test_password",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.show-sql=false",
      "app.file.upload-dir=/tmp/f2r-oauth-defaults-files",
      "logging.level.org.hibernate.SQL=WARN"
    })
@EnabledIfEnvironmentVariable(named = "F2R_STUDIO_MYSQL_TEST", matches = "true")
class McpOAuthDefaultsHttpTest {
  static final String CHATGPT = "https://chatgpt.com/connector/oauth/Zx12_ab-9";
  @Autowired UserRepository users;
  @Autowired JwtTokenProvider jwt;
  @Autowired AdminSessionValidator stamps;
  @Autowired ObjectMapper json;
  final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  final String base = "http://127.0.0.1:18089";

  String cookie(String login, Role role) {
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
    return "ACCESS_TOKEN="
        + jwt.issue(u.getUserId(), u.getLoginId(), u.getRole().name(), stamps.stampFor(u.getUserId()));
  }

  HttpResponse<String> register(String... uris) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(base + "/mcp/oauth/register"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(
                        Map.of("client_name", "defaults test", "redirect_uris", List.of(uris)))))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  String clientFor(String uri) throws Exception {
    var r = register(uri);
    assertEquals(200, r.statusCode(), r.body());
    return json.readTree(r.body()).get("client_id").asText();
  }

  Map<String, String> query(String client, String redirect) {
    return new LinkedHashMap<>(
        Map.of(
            "client_id", client,
            "redirect_uri", redirect,
            "response_type", "code",
            "state", "st-1",
            "code_challenge_method", "S256",
            "code_challenge", McpOAuth.challenge("v".repeat(43)),
            "resource", base + "/mcp"));
  }

  HttpResponse<String> authorizePage(Map<String, String> q, String cookie) throws Exception {
    String qs =
        q.entrySet().stream()
            .map(e -> McpOAuth.encode(e.getKey()) + "=" + McpOAuth.encode(e.getValue()))
            .collect(Collectors.joining("&"));
    return http.send(
        HttpRequest.newBuilder(URI.create(base + "/mcp/oauth/authorize?" + qs))
            .header("Cookie", cookie)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> consent(String cookie, String nonce, String scopes, boolean ack)
      throws Exception {
    String form =
        "nonce=" + McpOAuth.encode(nonce) + scopes + (ack ? "&adminAck=true" : "");
    return http.send(
        HttpRequest.newBuilder(URI.create(base + "/mcp/oauth/authorize"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", cookie)
            .header("Origin", base)
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  String nonce(HttpResponse<String> page) {
    return org.jsoup.Jsoup.parse(page.body()).select("input[name=nonce]").attr("value");
  }

  @Test
  void givenOfficialCallbackShapes_whenRegistering_thenAcceptedAndOthersRejected() throws Exception {
    // given / when / then
    assertEquals(200, register(CHATGPT).statusCode());
    assertEquals(200, register("https://chatgpt.com/connector_platform_oauth_redirect").statusCode());
    assertEquals(200, register("https://claude.ai/api/mcp/auth_callback").statusCode());
    assertEquals(200, register("https://claude.com/api/mcp/auth_callback").statusCode());
    assertEquals(400, register("https://evil.example/connector/oauth/abc").statusCode());
    assertEquals(400, register("https://chatgpt.com/connector/oauth/a/b").statusCode());
    assertEquals(400, register("https://chatgpt.com/connector/oauth/abc?x=1").statusCode());
    assertEquals(400, register("http://localhost:8080/cb").statusCode());
  }

  @Test
  void givenAdminChoosingSensitiveScope_whenNoAcknowledgement_thenRejectedUntilAcknowledged()
      throws Exception {
    // given
    String client = clientFor(CHATGPT);
    String admin = cookie("oauth-defaults-admin", Role.ADMIN);
    var page = authorizePage(query(client, CHATGPT), admin);
    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains("관리자 계정 경고"));
    String scopes = "&scopes=catalog%3Aread&scopes=easy%3Aread";
    // when
    var denied = consent(admin, nonce(page), scopes, false);
    // then
    assertEquals(400, denied.statusCode());
    assertTrue(denied.body().contains("admin_consent_required"));
    // when: 같은 동의 화면을 새로 열어 확인란을 체크
    var again = authorizePage(query(client, CHATGPT), admin);
    var allowed = consent(admin, nonce(again), scopes, true);
    // then
    assertEquals(303, allowed.statusCode());
    String location = allowed.headers().firstValue("location").orElseThrow();
    assertTrue(location.startsWith(CHATGPT + "?code="));
    assertTrue(location.endsWith("&state=st-1"));
  }

  @Test
  void givenAdminChoosingOnlyStudioScope_whenConsenting_thenNoAcknowledgementNeeded()
      throws Exception {
    // given
    String client = clientFor("https://claude.com/api/mcp/auth_callback");
    String admin = cookie("oauth-defaults-admin", Role.ADMIN);
    var page = authorizePage(query(client, "https://claude.com/api/mcp/auth_callback"), admin);
    // when
    var result = consent(admin, nonce(page), "&scopes=catalog%3Aread&scopes=studio%3Aread", false);
    // then
    assertEquals(303, result.statusCode());
  }

  @Test
  void givenRegisteredClient_whenUsingDifferentRedirectOrBrokenParams_thenAuthorizationRefused()
      throws Exception {
    // given
    String client = clientFor(CHATGPT);
    String user = cookie("oauth-defaults-admin", Role.ADMIN);
    // when / then: 등록하지 않은 다른 허용 URI, 상태 누락, 잘못된 resource·PKCE
    assertEquals(
        400, authorizePage(query(client, "https://claude.ai/api/mcp/auth_callback"), user).statusCode());
    var noState = query(client, CHATGPT);
    noState.put("state", "");
    assertEquals(400, authorizePage(noState, user).statusCode());
    var wrongResource = query(client, CHATGPT);
    wrongResource.put("resource", "https://other.example/mcp");
    assertEquals(400, authorizePage(wrongResource, user).statusCode());
    var plainPkce = query(client, CHATGPT);
    plainPkce.put("code_challenge_method", "plain");
    assertEquals(400, authorizePage(plainPkce, user).statusCode());
  }

  @Test
  void givenMissingOrForgedBearer_whenCallingMcp_thenUnauthorizedWithDiscoveryHeader()
      throws Exception {
    // given
    var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
    // when
    var none =
        http.send(
            HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    var forged =
        http.send(
            HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer forged-token")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    // then
    for (var r : List.of(none, forged)) {
      assertEquals(401, r.statusCode());
      assertTrue(
          r.headers().firstValue("WWW-Authenticate").orElse("").contains("resource_metadata="));
    }
  }
}
