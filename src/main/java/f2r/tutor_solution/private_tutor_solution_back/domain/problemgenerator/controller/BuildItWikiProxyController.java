package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiIdentitySigner;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository.QuestionWikiRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.support.QuestionAiGenerationPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String QUESTION_WIKI_REFERENCE_MARKER = "QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST:";
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final WikiIdentitySigner identitySigner;
  private final QuestionWikiRepository questionWikiRepository;
  private final ConcurrentMap<Long, Deque<Instant>> questionWikiAiSuccesses = new ConcurrentHashMap<>();

  public BuildItWikiProxyController(
      AuthenticatedUserResolver authenticatedUserResolver,
      WikiIdentitySigner identitySigner,
      QuestionWikiRepository questionWikiRepository) {
    this.authenticatedUserResolver = authenticatedUserResolver;
    this.identitySigner = identitySigner;
    this.questionWikiRepository = questionWikiRepository;
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
    boolean quotaExempt = isQuestionWikiAiQuotaExempt(principal);
    if (questionWikiAi && principal != null && !quotaExempt
        && hasReachedQuestionWikiAiLimit(principal.userId(), Instant.now())) {
      return normalizedGatewayError(HttpStatus.TOO_MANY_REQUESTS.value(),
          "AI 문제제작은 1인당 1시간에 최대 20회까지 이용할 수 있습니다.");
    }

    String query = request.getQueryString();
    URI uri = URI.create("http://127.0.0.1:8000" + targetPath + (query == null ? "" : "?" + query));
    byte[] body = request.getInputStream().readAllBytes();
    // 2026년 9월 모의고사 문항은 AI 문제제작 대상이 아니다 — Reference 마커가 가리키는 문항이
    // 해당 회차면 라우터로 넘기지 않고 차단한다. 마커가 없는 다른 AI 호출에는 영향을 주지 않는다.
    if (questionWikiAi && isAiGenerationDisabledQuestion(body)) {
      return normalizedGatewayError(HttpStatus.FORBIDDEN.value(),
          "2026년 9월 모의고사 문항은 AI 문제제작을 제공하지 않습니다.");
    }
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .timeout(questionWikiAi ? QUESTION_WIKI_AI_TIMEOUT : WIKI_REQUEST_TIMEOUT)
        .method(request.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
    String contentType = request.getHeader("Content-Type");
    if (contentType != null) builder.header("Content-Type", contentType);
    if (principal != null) identitySigner.sign(builder, principal);
    if (principal != null && Boolean.TRUE.equals(request.getAttribute("f2r.mcp.readOnly"))) {
      identitySigner.signReadOnly(builder, principal, targetPath + (query == null ? "" : "?" + query));
    }
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
    if (questionWikiAi && principal != null && !quotaExempt
        && response.statusCode() >= 200 && response.statusCode() < 300) {
      recordQuestionWikiAiSuccess(principal.userId(), Instant.now());
    }
    ResponseEntity.BodyBuilder out = ResponseEntity.status(response.statusCode());
    response.headers().firstValue("content-type").ifPresent(v -> out.header(HttpHeaders.CONTENT_TYPE, v));
    response.headers().firstValue("content-disposition").ifPresent(v -> out.header(HttpHeaders.CONTENT_DISPOSITION, v));
    if (isMcpPath(targetPath)) response.headers().firstValue("mcp-session-id").ifPresent(v -> out.header("Mcp-Session-Id", v));
    return out.body(response.body());
  }

  private boolean isQuestionWikiAiQuotaExempt(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && "ADMIN".equals(principal.role());
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

  /** 요청 본문의 Question Wiki Reference 마커가 AI 문제제작 비제공 회차 문항을 가리키는지 판정한다. */
  private boolean isAiGenerationDisabledQuestion(byte[] body) {
    Long questionWikiId = extractQuestionWikiId(body);
    if (questionWikiId == null) {
      return false;
    }
    return questionWikiRepository.findById(questionWikiId)
        .map(QuestionAiGenerationPolicy::isAiGenerationDisabled)
        .orElse(false);
  }

  /**
   * 본문 JSON의 prompt에서 {@code QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST: {...}} 마커의
   * questionWikiId를 꺼낸다. 마커가 없거나 해석할 수 없으면 null(차단하지 않음).
   */
  static Long extractQuestionWikiId(byte[] body) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(body);
      String prompt = root.path("prompt").asText(null);
      if (prompt == null) {
        return null;
      }
      int marker = prompt.indexOf(QUESTION_WIKI_REFERENCE_MARKER);
      if (marker < 0) {
        return null;
      }
      int start = prompt.indexOf('{', marker);
      int end = start < 0 ? -1 : prompt.indexOf('}', start);
      if (start < 0 || end < 0) {
        return null;
      }
      long id = OBJECT_MAPPER.readTree(prompt.substring(start, end + 1)).path("questionWikiId").asLong(0);
      return id > 0 ? id : null;
    } catch (Exception e) {
      return null;
    }
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
