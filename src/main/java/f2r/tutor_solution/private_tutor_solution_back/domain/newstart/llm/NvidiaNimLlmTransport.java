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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * NVIDIA NIM DIRECT 호출(OpenAI-compatible chat completions). NewStart 지피지기 primary.
 *
 * <p>공식 sample 확정 계약: {@code temperature=1}, {@code top_p=0.95}, {@code max_tokens=16384},
 * {@code chat_template_kwargs.enable_thinking=true}, {@code reasoning_budget=16384}. 최초 baseline이고
 * 값은 모두 {@link NewStartLlmProperties.NvidiaNim}으로 config화했다.
 *
 * <p>assistant {@code content}만 최종 답변으로 쓴다. {@code reasoning_content}(내부 사고)는 읽지 않고
 * 폐기하며, 어떤 응답·로그·예외 메시지에도 실리지 않는다.
 */
@Component
public class NvidiaNimLlmTransport implements NewStartLlmTransport {

  private static final Logger log = LoggerFactory.getLogger(NvidiaNimLlmTransport.class);

  private static final String GENERIC_ERROR = "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.";

  private final NewStartLlmProperties.NvidiaNim config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public NvidiaNimLlmTransport(NewStartLlmProperties properties) {
    this.config = properties.getNvidia();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  @Override
  public String name() {
    return "nvidia-nim";
  }

  @Override
  public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
    if (config.getApiKey() == null || config.getApiKey().isBlank()) {
      // 키 미설정은 "연결이 실패한 것"이 아니라 설정 상태다. §4의 재시도 대상이 아니므로
      // fallback(Qwen)으로 넘기지 않고, 설정상 세부(provider명·사유)를 회원 응답에 내리지 않는다.
      return NewStartLlmCompletion.failed(GENERIC_ERROR, false);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", config.getModel());
    body.put("messages", List.of(
        Map.of("role", "system", "content", systemPrompt == null || systemPrompt.isBlank() ? " " : systemPrompt),
        Map.of("role", "user", "content", prompt)));
    body.put("temperature", config.getTemperature());
    body.put("top_p", config.getTopP());
    body.put("max_tokens", config.getMaxTokens());
    body.put("chat_template_kwargs", Map.of("enable_thinking", config.isEnableThinking()));
    body.put("reasoning_budget", config.getReasoningBudget());
    long tStart = System.nanoTime();
    try {
      String payload = objectMapper.writeValueAsString(body);
      HttpRequest request = HttpRequest
          .newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
          .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + config.getApiKey())
          .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
          .build();
      long tSend = System.nanoTime();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      long tHttp = System.nanoTime();
      if (response.statusCode() != 200) {
        // §4: 429·5xx만 재시도 대상. 그 외 4xx(인증·요건 오류)는 fallback 없이 통제된 실패로 알린다.
        boolean eligible = response.statusCode() == 429 || response.statusCode() >= 500;
        log.info("newstart nvidia call status={} httpMs={} requestChars={} maxTokens={} reasoningEnabled={} reasoningBudget={}",
            response.statusCode(), ms(tHttp - tSend), payload.length(), config.getMaxTokens(),
            config.isEnableThinking(), config.getReasoningBudget());
        return NewStartLlmCompletion.failed(GENERIC_ERROR, eligible);
      }
      CallStats stats = new CallStats();
      NewStartLlmCompletion completion = parse(response.body(), stats);
      log.info("newstart nvidia call status={} httpMs={} parseMs={} ok={} provider={} model={} "
              + "requestChars={} contentChars={} finishReason={} promptTokens={} completionTokens={} "
              + "totalTokens={} reasoningTokens={} maxTokens={} reasoningEnabled={} reasoningBudget={}",
          response.statusCode(), ms(tHttp - tSend), ms(System.nanoTime() - tHttp), completion.ok(),
          completion.provider(), completion.model(), payload.length(), stats.contentChars, stats.finishReason,
          stats.promptTokens, stats.completionTokens, stats.totalTokens, stats.reasoningTokens,
          config.getMaxTokens(), config.isEnableThinking(), config.getReasoningBudget());
      return completion;
    } catch (IOException exception) {
      log.info("newstart nvidia call ioFailure httpMs={} maxTokens={} reasoningEnabled={} reasoningBudget={}",
          ms(System.nanoTime() - tStart), config.getMaxTokens(), config.isEnableThinking(),
          config.getReasoningBudget());
      return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
    }
  }

  /** 스레드safe 유지 위해 호출 1건 지역의 카운터(빈 필드에 누적하지 않는다). -1은 provider가 usage를 안 준 경우. */
  private static final class CallStats {
    private int contentChars;
    private String finishReason = "";
    private int promptTokens = -1;
    private int completionTokens = -1;
    private int totalTokens = -1;
    private int reasoningTokens = -1;
  }

  private NewStartLlmCompletion parse(byte[] responseBody, CallStats stats) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
      }
      JsonNode choice = choices.get(0);
      String content = assistantContent(choice.path("message").path("content"));
      stats.contentChars = content.length();
      stats.finishReason = choice.path("finish_reason").asText("");
      // reasoning_content 값은 읽지 않는다. 소진 token 수만 구간 분석용 카운터로 쓴다.
      JsonNode usage = root.path("usage");
      stats.promptTokens = usage.path("prompt_tokens").asInt(-1);
      stats.completionTokens = usage.path("completion_tokens").asInt(-1);
      stats.totalTokens = usage.path("total_tokens").asInt(-1);
      stats.reasoningTokens = usage.path("reasoning_tokens").asInt(-1);
      if ("interrupted".equals(stats.finishReason)) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
      }
      if (content.isBlank()) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
      }
      return NewStartLlmCompletion.success(content.strip(),
          root.path("provider").asText(name()), root.path("model").asText(config.getModel()));
    } catch (IOException exception) {
      return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
    }
  }

  private static long ms(long nanos) {
    return Math.round(nanos / 1_000_000.0);
  }

  /** NIM은 content를 문자열 또는 text part 배열로 반환한다. reasoning_content는 여기서 읽지 않는다. */
  private static String assistantContent(JsonNode content) {
    if (content.isArray()) {
      StringBuilder text = new StringBuilder();
      for (JsonNode part : content) {
        if (part.isObject()) {
          text.append(part.path("text").asText(""));
        }
      }
      return text.toString();
    }
    return content.asText("");
  }
}
