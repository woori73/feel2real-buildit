package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildItWikiProxyController {
  private static final Duration WIKI_REQUEST_TIMEOUT = Duration.ofSeconds(280);
  private static final String MCP_PATH_PREFIX = "/api/mcp/";
  // MCP Streamable HTTP 스펙(및 mcp_server.py의 Bearer 미들웨어)이 요구하는 헤더만 골라 전달한다.
  // Authorization이 없으면 FastAPI 쪽 Bearer 인증이 항상 401을 내므로 이게 근본 원인이었다.
  private static final String[] MCP_FORWARDED_REQUEST_HEADERS = {
      "Authorization", "Accept", "Mcp-Session-Id", "Mcp-Protocol-Version", "Last-Event-ID"
  };
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @RequestMapping("/problem-generator/wiki/api/**")
  public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws Exception {
    String prefix = "/problem-generator/wiki";
    String targetPath = request.getRequestURI().substring(prefix.length());
    String query = request.getQueryString();
    URI uri = URI.create("http://127.0.0.1:8000" + targetPath + (query == null ? "" : "?" + query));
    byte[] body = request.getInputStream().readAllBytes();
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .timeout(WIKI_REQUEST_TIMEOUT)
        .method(request.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
    String contentType = request.getHeader("Content-Type");
    if (contentType != null) builder.header("Content-Type", contentType);
    if (targetPath.startsWith(MCP_PATH_PREFIX)) {
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
    if (targetPath.startsWith(MCP_PATH_PREFIX)) {
      response.headers().firstValue("mcp-session-id").ifPresent(v -> out.header("Mcp-Session-Id", v));
    }
    return out.body(response.body());
  }

  private ResponseEntity<byte[]> normalizedGatewayError(int status, String message) {
    String json = "{\"detail\":{\"message\":\"" + message
        + "\",\"provider\":\"ai-proxy\",\"status\":" + status + "}}";
    return ResponseEntity.status(status)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(json.getBytes(StandardCharsets.UTF_8));
  }
}

