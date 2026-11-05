package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared answer transport only: callers retain their own retrieval and prompts. */
@Component
public class OpenAiLunaLlmTransport implements NewStartLlmTransport {
  private static final Logger log = LoggerFactory.getLogger(OpenAiLunaLlmTransport.class);
  private static final String ERROR = "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.";
  private static final String MODEL = "gpt-6-luna";
  private final String apiKey;
  private final HttpClient client;
  private final URI endpoint;
  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired
  public OpenAiLunaLlmTransport(@Value("${OPENAI_API_KEY:}") String apiKey) {
    this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        URI.create("https://api.openai.com/v1/responses"));
  }

  OpenAiLunaLlmTransport(String apiKey, HttpClient client, URI endpoint) {
    this.apiKey = apiKey;
    this.client = client;
    this.endpoint = endpoint;
  }

  @Override
  public String name() { return "openai-luna"; }

  public boolean available() { return apiKey != null && !apiKey.isBlank(); }

  public NewStartLlmCompletion completeWithImage(String systemPrompt, String prompt, String dataUrl, int maxOutputChars) {
    return completeInput(systemPrompt, List.of(Map.of("role", "user", "content", List.of(
        Map.of("type", "input_text", "text", prompt),
        Map.of("type", "input_image", "image_url", dataUrl)))), maxOutputChars);
  }

  @Override
  public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
    return completeInput(systemPrompt, prompt == null || prompt.isBlank() ? " " : prompt, maxOutputChars);
  }

  public NewStartLlmCompletion completeMessages(String systemPrompt, List<Map<String, Object>> messages, int maxOutputChars) {
    return completeInput(systemPrompt, messages, maxOutputChars);
  }

  private NewStartLlmCompletion completeInput(String systemPrompt, Object input, int maxOutputChars) {
    if (apiKey == null || apiKey.isBlank()) {
      return NewStartLlmCompletion.failed(ERROR, false);
    }
    try {
      String payload = mapper.writeValueAsString(Map.of(
          "model", MODEL,
          "instructions", systemPrompt == null || systemPrompt.isBlank() ? " " : systemPrompt,
          "input", input,
          "reasoning", Map.of("effort", "low"),
          "max_output_tokens", Math.min(16384, Math.max(4096, maxOutputChars)),
          "store", false));
      HttpRequest request = HttpRequest.newBuilder(endpoint)
          .timeout(Duration.ofSeconds(90))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status != 200) {
        log.info("openai-luna status={}", status);
        return NewStartLlmCompletion.failed(ERROR, status == 429 || status >= 500);
      }
      return parse(response.body(), maxOutputChars);
    } catch (IOException exception) {
      return NewStartLlmCompletion.failed(ERROR, true);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return NewStartLlmCompletion.failed(ERROR, true);
    }
  }

  NewStartLlmCompletion parse(String body, int maxOutputChars) {
    try {
      JsonNode root = mapper.readTree(body);
      if (root == null || !"completed".equals(root.path("status").asText())) {
        return NewStartLlmCompletion.failed(ERROR, true);
      }
      StringBuilder text = new StringBuilder();
      for (JsonNode item : root.path("output")) {
        if (!"message".equals(item.path("type").asText())
            || !"assistant".equals(item.path("role").asText())) continue;
        for (JsonNode part : item.path("content")) {
          if ("output_text".equals(part.path("type").asText())) {
            text.append(part.path("text").asText(""));
          }
        }
      }
      String answer = text.toString().strip();
      if (answer.isBlank()) return NewStartLlmCompletion.failed(ERROR, true);
      JsonNode usage = root.path("usage");
      log.info("openai-luna model={} inputTokens={} cachedInputTokens={} outputTokens={}", MODEL,
          usage.path("input_tokens").asLong(),
          usage.path("input_tokens_details").path("cached_tokens").asLong(),
          usage.path("output_tokens").asLong());
      if (maxOutputChars > 0 && answer.length() > maxOutputChars) {
        answer = answer.substring(0, maxOutputChars);
      }
      return NewStartLlmCompletion.success(answer, name(), MODEL);
    } catch (IOException | IllegalArgumentException exception) {
      return NewStartLlmCompletion.failed(ERROR, true);
    }
  }
}
