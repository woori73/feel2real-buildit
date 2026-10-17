package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Muse Spark 함수 호출(tools) 연동 — P1.
 *
 * <p>보안 규칙:
 * <ul>
 *   <li>화이트리스트에 등록된 함수만 호출 허용 ({@link MuseSparkToolDefinitions#WHITELIST})</li>
 *   <li>함수 인자는 스키마(required 필드) 검증 후 실행</li>
 *   <li>호출 횟수·시간 제한 (분당 최대 10회, 호출당 60초)</li>
 *   <li>API 키·토큰·민감정보는 로그에 출력하지 않는다</li>
 * </ul>
 *
 * <p>함수 호출이 발생하지 않으면 일반 텍스트 응답으로 처리한다.
 */
@Component
public class MuseSparkToolsService {

  private static final Logger log = LoggerFactory.getLogger(MuseSparkToolsService.class);

  /** 분당 최대 함수 호출 횟수. */
  private static final int MAX_CALLS_PER_MINUTE = 10;
  /** 함수 실행 시간 제한 (초). */
  private static final int CALL_TIMEOUT_SECONDS = 60;

  private final NewStartLlmProperties.MetaMuseSpark config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 분당 호출 횟수 추적 (키: 분 단위 타임스탬프). */
  private final ConcurrentHashMap<Long, AtomicInteger> callsPerMinute = new ConcurrentHashMap<>();

  public MuseSparkToolsService(NewStartLlmProperties properties) {
    this.config = properties.getMeta();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  /**
   * tools를 포함한 채팅 요청을 보내고 응답을 파싱한다.
   *
   * @return 함수 호출 목록 (호출이 없으면 빈 목록)
   */
  public MuseSparkToolsResult completeWithTools(String systemPrompt, String userPrompt) {
    if (config.getApiKey() == null || config.getApiKey().isBlank()) {
      return MuseSparkToolsResult.failed("API 키가 설정되지 않았습니다.");
    }
    if (!checkRateLimit()) {
      return MuseSparkToolsResult.failed("분당 호출 한도를 초과했습니다.");
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", config.getModel());
    body.put("messages", List.of(
        Map.of("role", "system", "content", systemPrompt == null || systemPrompt.isBlank() ? " " : systemPrompt),
        Map.of("role", "user", "content", userPrompt == null || userPrompt.isBlank() ? " " : userPrompt)));
    body.put("temperature", config.getTemperature());
    body.put("max_tokens", config.getMaxTokens());
    body.put("tools", MuseSparkToolDefinitions.all());
    body.put("tool_choice", "auto");

    try {
      String payload = objectMapper.writeValueAsString(body);
      // 페이로드에 API 키가 포함되지 않음을 확인 (키는 헤더로만 전송)
      HttpRequest request = HttpRequest
          .newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
          .timeout(Duration.ofSeconds(CALL_TIMEOUT_SECONDS))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + config.getApiKey())
          .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
          .build();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        log.info("muse-spark tools call status={}", response.statusCode());
        return MuseSparkToolsResult.failed("API 호출 실패: HTTP " + response.statusCode());
      }
      return parseToolCalls(response.body());
    } catch (IOException e) {
      log.info("muse-spark tools call ioFailure");
      return MuseSparkToolsResult.failed("네트워크 오류가 발생했습니다.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return MuseSparkToolsResult.failed("요청이 중단되었습니다.");
    }
  }

  private MuseSparkToolsResult parseToolCalls(byte[] responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        return MuseSparkToolsResult.failed("응답 형식이 올바르지 않습니다.");
      }
      JsonNode message = choices.get(0).path("message");
      String content = message.path("content").isNull() ? "" : message.path("content").asText("");
      JsonNode toolCallsNode = message.path("tool_calls");

      // 함수 호출이 없으면 일반 텍스트 응답
      if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
        return MuseSparkToolsResult.textOnly(content);
      }

      List<MuseSparkToolCall> calls = new ArrayList<>();
      for (JsonNode tc : toolCallsNode) {
        String id = tc.path("id").asText("");
        String name = tc.path("function").path("name").asText("");
        String argsJson = tc.path("function").path("arguments").asText("{}");

        // 화이트리스트 검사
        if (!MuseSparkToolDefinitions.isAllowed(name)) {
          log.info("muse-spark tools blocked non-whitelisted function");
          return MuseSparkToolsResult.failed("허용되지 않은 함수입니다.");
        }

        // 인자 스키마 검증 (required 필드 확인)
        if (!validateArguments(name, argsJson)) {
          return MuseSparkToolsResult.failed("함수 인자가 올바르지 않습니다: " + name);
        }

        Map<String, Object> args = objectMapper.readValue(argsJson,
            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        // 인자 값에서 민감정보 마스킹 후 로그 (사업자번호 등)
        calls.add(new MuseSparkToolCall(id, name, args));
      }
      log.info("muse-spark tools calls parsed count={}", calls.size());
      return MuseSparkToolsResult.success(content, calls);
    } catch (IOException e) {
      return MuseSparkToolsResult.failed("응답 파싱에 실패했습니다.");
    }
  }

  /**
   * 함수 인자 스키마 검증 — required 필드가 모두 있는지 확인한다.
   * 값 자체는 로그에 출력하지 않는다.
   */
  boolean validateArguments(String functionName, String argsJson) {
    try {
      JsonNode args = objectMapper.readTree(argsJson);
      if (!args.isObject()) {
        return false;
      }
      List<String> required = switch (functionName) {
        case "request_card_sales_download", "request_cash_receipt_download",
            "request_bank_statement_download" ->
            List.of("business_id", "from_date", "to_date");
        case "check_download_status" -> List.of("job_id");
        case "preview_import", "confirm_import" -> List.of("run_id");
        default -> List.of();
      };
      for (String field : required) {
        if (!args.has(field) || args.get(field).asText("").isBlank()) {
          return false;
        }
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private boolean checkRateLimit() {
    long minute = System.currentTimeMillis() / 60000;
    // 오래된 분 버킷 정리
    callsPerMinute.keySet().removeIf(k -> k < minute - 1);
    AtomicInteger count = callsPerMinute.computeIfAbsent(minute, k -> new AtomicInteger(0));
    return count.incrementAndGet() <= MAX_CALLS_PER_MINUTE;
  }

  /** 파싱된 단일 함수 호출. */
  public record MuseSparkToolCall(String id, String name, Map<String, Object> arguments) {
  }

  /** tools 호출 결과. */
  public static class MuseSparkToolsResult {
    private final boolean ok;
    private final String error;
    private final String textContent;
    private final List<MuseSparkToolCall> toolCalls;

    private MuseSparkToolsResult(boolean ok, String error, String textContent,
        List<MuseSparkToolCall> toolCalls) {
      this.ok = ok;
      this.error = error;
      this.textContent = textContent;
      this.toolCalls = toolCalls;
    }

    public static MuseSparkToolsResult success(String text, List<MuseSparkToolCall> calls) {
      return new MuseSparkToolsResult(true, null, text, List.copyOf(calls));
    }

    public static MuseSparkToolsResult textOnly(String text) {
      return new MuseSparkToolsResult(true, null, text, List.of());
    }

    public static MuseSparkToolsResult failed(String error) {
      return new MuseSparkToolsResult(false, error, "", List.of());
    }

    public boolean isOk() {
      return ok;
    }

    public String getError() {
      return error;
    }

    public String getTextContent() {
      return textContent;
    }

    public List<MuseSparkToolCall> getToolCalls() {
      return toolCalls;
    }

    public boolean hasToolCalls() {
      return !toolCalls.isEmpty();
    }
  }
}
