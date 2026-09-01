package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildItWikiProxyController {
  private static final Duration WIKI_REQUEST_TIMEOUT = Duration.ofSeconds(280);
  private static final String MCP_PATH = "/api/mcp";
  // MCP Streamable HTTP 스펙(및 mcp_server.py의 Bearer/X-Api-Key 미들웨어)이 요구하는 헤더만
  // 골라 전달한다. X-Api-Key는 Authorization 헤더를 커스텀 값으로 못 보내는 MCP client(예:
  // Claude Custom Connector)를 위한 대안 인증 경로다 — mcp_server.py가 둘 중 하나만 맞아도
  // 통과시킨다.
  private static final String[] MCP_FORWARDED_REQUEST_HEADERS = {
      "Authorization", "X-Api-Key", "Accept", "Mcp-Session-Id", "Mcp-Protocol-Version", "Last-Event-ID"
  };
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final byte[] identitySecret;

  public BuildItWikiProxyController(
      AuthenticatedUserResolver authenticatedUserResolver,
      @Value("${app.wiki.identity-secret:${app.jwt.secret}}") String identitySecret) {
    this.authenticatedUserResolver = authenticatedUserResolver;
    this.identitySecret = identitySecret.getBytes(StandardCharsets.UTF_8);
    if (this.identitySecret.length < 32) {
      throw new IllegalStateException("Wiki identity secret must be at least 32 bytes");
    }
  }

  @RequestMapping("/problem-generator/wiki/api/**")
  public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws Exception {
    String prefix = "/problem-generator/wiki";
    String targetPath = request.getRequestURI().substring(prefix.length());
    JwtTokenProvider.AuthenticatedPrincipal principal = authenticatedUserResolver.currentPrincipal()
        .orElse(null);
    // External MCP clients keep their existing API-key authentication. Browser Wiki APIs
    // always require the JWT principal and never accept identity supplied by the client.
    if (principal == null && !isMcpPath(targetPath)) {
      return normalizedGatewayError(HttpStatus.UNAUTHORIZED.value(), "로그인이 필요합니다.");
    }
    String query = request.getQueryString();
    URI uri = URI.create("http://127.0.0.1:8000" + targetPath + (query == null ? "" : "?" + query));
    byte[] body = request.getInputStream().readAllBytes();
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .timeout(WIKI_REQUEST_TIMEOUT)
        .method(request.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
    String contentType = request.getHeader("Content-Type");
    if (contentType != null) builder.header("Content-Type", contentType);
    if (principal != null) addTrustedIdentity(builder, principal);
    if (isMcpPath(targetPath)) {
      for (String headerName : MCP_FORWARDED_REQUEST_HEADERS) {
        String value = request.getHeader(headerName);
        if (value != null) builder.header(headerName, value);
      }
    }
    HttpResponse<byte[]> response;
    try {
      response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (HttpTimeoutException exception) {
      return normalizedGatewayError(504, "AI 응답 시간이 초과되었습니다. 다시 시도해 주세요.");
    } catch (java.io.IOException exception) {
      return normalizedGatewayError(502, "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
    ResponseEntity.BodyBuilder out = ResponseEntity.status(response.statusCode());
    response.headers().firstValue("content-type").ifPresent(v -> out.header(HttpHeaders.CONTENT_TYPE, v));
    response.headers().firstValue("content-disposition").ifPresent(v -> out.header(HttpHeaders.CONTENT_DISPOSITION, v));
    if (isMcpPath(targetPath)) {
      response.headers().firstValue("mcp-session-id").ifPresent(v -> out.header("Mcp-Session-Id", v));
    }
    return out.body(response.body());
  }

  private void addTrustedIdentity(
      HttpRequest.Builder builder, JwtTokenProvider.AuthenticatedPrincipal principal) throws Exception {
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    String payload = principal.userId() + "\n" + principal.loginId() + "\n"
        + principal.role() + "\n" + timestamp;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(identitySecret, "HmacSHA256"));
    String signature = java.util.HexFormat.of().formatHex(
        mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    builder.header("X-BuildIt-User-Id", principal.userId().toString());
    builder.header("X-BuildIt-Login-Id", principal.loginId());
    builder.header("X-BuildIt-Role", principal.role());
    builder.header("X-BuildIt-Timestamp", timestamp);
    builder.header("X-BuildIt-Signature", signature);
  }

  private boolean isMcpPath(String targetPath) {
    return targetPath.equals(MCP_PATH) || targetPath.startsWith(MCP_PATH + "/");
  }

  private ResponseEntity<byte[]> normalizedGatewayError(int status, String message) {
    String json = "{\"detail\":{\"message\":\"" + message
        + "\",\"provider\":\"ai-proxy\",\"status\":" + status + "}}";
    return ResponseEntity.status(status)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(json.getBytes(StandardCharsets.UTF_8));
  }
}
