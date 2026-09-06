package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiIdentitySigner;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildItWikiProxyController {
  private static final Duration WIKI_REQUEST_TIMEOUT = Duration.ofSeconds(280);
  private static final Duration QUESTION_WIKI_AI_TIMEOUT = Duration.ofSeconds(120);
  private static final Duration QUESTION_WIKI_AI_WINDOW = Duration.ofHours(1);
  private static final int QUESTION_WIKI_AI_SUCCESS_LIMIT = 20;
  private static final String MCP_PATH = "/api/mcp";
  private static final String[] MCP_FORWARDED_REQUEST_HEADERS = {
      "Authorization", "X-Api-Key", "Accept", "Mcp-Session-Id", "Mcp-Protocol-Version", "Last-Event-ID"
  };
  private static final String WIKI_WORKSPACE_HEADER = "X-BuildIt-Wiki-Workspace";
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final WikiIdentitySigner identitySigner;
  private final ConcurrentMap<Long, Deque<Instant>> questionWikiAiSuccesses = new ConcurrentHashMap<>();

  public BuildItWikiProxyController(
      AuthenticatedUserResolver authenticatedUserResolver,
      WikiIdentitySigner identitySigner) {
    this.authenticatedUserResolver = authenticatedUserResolver;
    this.identitySigner = identitySigner;
  }

  @RequestMapping("/problem-generator/wiki/api/**")
  public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws Exception {
    String prefix = "/problem-generator/wiki";
    String targetPath = request.getRequestURI().substring(prefix.length());
    JwtTokenProvider.AuthenticatedPrincipal principal = authenticatedUserResolver.currentPrincipal().orElse(null);
    if (principal == null && !isMcpPath(targetPath)) {
      return normalizedGatewayError(HttpStatus.UNAUTHORIZED.value(), "로그인이 필요합니다.");
    }
    boolean questionWikiAi = isQuestionWikiAi(targetPath);
    if (questionWikiAi && principal != null && hasReachedQuestionWikiAiLimit(principal.userId(), Instant.now())) {
      return normalizedGatewayError(HttpStatus.TOO_MANY_REQUESTS.value(),
          "AI 문제제작은 1인당 1시간에 최대 20회까지 이용할 수 있습니다.");
    }

    String query = request.getQueryString();
    URI uri = URI.create("http://127.0.0.1:8000" + targetPath + (query == null ? "" : "?" + query));
    byte[] body = request.getInputStream().readAllBytes();
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .timeout(questionWikiAi ? QUESTION_WIKI_AI_TIMEOUT : WIKI_REQUEST_TIMEOUT)
        .method(request.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
    String contentType = request.getHeader("Content-Type");
    if (contentType != null) builder.header("Content-Type", contentType);
    if (principal != null) identitySigner.sign(builder, principal);
    if (principal != null) {
      String workspaceOverride = request.getHeader(WIKI_WORKSPACE_HEADER);
      if (workspaceOverride != null && !workspaceOverride.isBlank()) builder.header(WIKI_WORKSPACE_HEADER, workspaceOverride);
    }
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
    if (questionWikiAi && principal != null && response.statusCode() >= 200 && response.statusCode() < 300) {
      recordQuestionWikiAiSuccess(principal.userId(), Instant.now());
    }
    ResponseEntity.BodyBuilder out = ResponseEntity.status(response.statusCode());
    response.headers().firstValue("content-type").ifPresent(v -> out.header(HttpHeaders.CONTENT_TYPE, v));
    response.headers().firstValue("content-disposition").ifPresent(v -> out.header(HttpHeaders.CONTENT_DISPOSITION, v));
    if (isMcpPath(targetPath)) response.headers().firstValue("mcp-session-id").ifPresent(v -> out.header("Mcp-Session-Id", v));
    return out.body(response.body());
  }

  private boolean hasReachedQuestionWikiAiLimit(Long userId, Instant now) {
    Deque<Instant> successes = questionWikiAiSuccesses.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
    synchronized (successes) {
      evictExpired(successes, now);
      return successes.size() >= QUESTION_WIKI_AI_SUCCESS_LIMIT;
    }
  }

  private void recordQuestionWikiAiSuccess(Long userId, Instant now) {
    Deque<Instant> successes = questionWikiAiSuccesses.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
    synchronized (successes) {
      evictExpired(successes, now);
      successes.addLast(now);
    }
  }

  private void evictExpired(Deque<Instant> successes, Instant now) {
    Instant cutoff = now.minus(QUESTION_WIKI_AI_WINDOW);
    while (!successes.isEmpty() && !successes.peekFirst().isAfter(cutoff)) successes.removeFirst();
  }

  private boolean isQuestionWikiAi(String targetPath) {
    return targetPath.equals("/api/ai/complete");
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
