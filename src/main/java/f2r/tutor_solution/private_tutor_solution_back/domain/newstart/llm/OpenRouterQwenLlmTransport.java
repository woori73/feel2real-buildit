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
import org.springframework.stereotype.Component;

/**
 * OpenRouter Qwen DIRECT 호출 — NewStart 지피지기의 기술적 실패 전용 fallback.
 *
 * <p>요청 계약(model / messages / temperature)은 기존 공용 Wiki Router의 Qwen 경로와 동일하게
 * 유지하고, 라우터 프로세스를 거치지 않고 Java에서 바로 붙는다. primary와 완전히 같은
 * system prompt·question·근거 컨텍스트를 받으므로 재검색 없이 모델만 교체된다.
 */
@Component
public class OpenRouterQwenLlmTransport implements NewStartLlmTransport {

  private static final String GENERIC_ERROR = "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.";

  private final NewStartLlmProperties.OpenRouter config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public OpenRouterQwenLlmTransport(NewStartLlmProperties properties) {
    this.config = properties.getOpenrouter();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  @Override
  public String name() {
    return "openrouter";
  }

  @Override
  public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
    if (config.getApiKey() == null || config.getApiKey().isBlank()) {
      return NewStartLlmCompletion.failed("OpenRouter 키가 설정되지 않았습니다.", false);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", config.getModel());
    body.put("messages", List.of(
        Map.of("role", "system", "content", systemPrompt == null || systemPrompt.isBlank() ? " " : systemPrompt),
        Map.of("role", "user", "content", prompt)));
    body.put("temperature", config.getTemperature());
    try {
      HttpRequest request = HttpRequest
          .newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
          .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + config.getApiKey())
          .header("HTTP-Referer", "https://feel2real.co.kr")
          .header("X-Title", "BuildIt NewStart")
          .POST(HttpRequest.BodyPublishers.ofString(
              objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, false);
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, false);
      }
      String content = choices.get(0).path("message").path("content").asText("");
      if (content.isBlank()) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, false);
      }
      return NewStartLlmCompletion.success(content.strip(),
          root.path("provider").asText(name()), root.path("model").asText(config.getModel()));
    } catch (IOException exception) {
      return NewStartLlmCompletion.failed(GENERIC_ERROR, false);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return NewStartLlmCompletion.failed(GENERIC_ERROR, false);
    }
  }
}
