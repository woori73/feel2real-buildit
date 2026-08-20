package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChartGeminiClient {

  private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
  private static final String OPENAI_RESPONSES_API = "https://api.openai.com/v1/responses";
  private static final int TIMEOUT_SECONDS = 120;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

  @Value("${GEMINI_API_KEY:}")
  private String geminiApiKey;

  @Value("${GEMINI_MODEL:gemini-3.5-flash}")
  private String geminiModel;

  @Value("${OPENAI_API_KEY:}")
  private String openaiApiKey;

  @Value("${OPENAI_MODEL:chat-latest}")
  private String openaiModel;

  private volatile String runtimeProvider = "gemini";
  private volatile String runtimeApiKey;

  public boolean isReady() {
    String key = resolvedApiKey();
    return key != null && !key.isBlank();
  }

  public void setRuntimeApiKey(String provider, String apiKey) {
    if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("LLM API 키를 입력해 주세요.");
    this.runtimeProvider = normalizeProvider(provider);
    this.runtimeApiKey = apiKey.trim();
  }

  public void setRuntimeApiKey(String apiKey) {
    setRuntimeApiKey("gemini", apiKey);
  }

  public void clearRuntimeApiKey() {
    this.runtimeApiKey = null;
  }

  public String apiKeySource() {
    if (runtimeApiKey != null && !runtimeApiKey.isBlank()) return "runtime";
    String envKey = envApiKeyFor(provider());
    return envKey != null && !envKey.isBlank() ? "environment" : "missing";
  }

  public String provider() {
    if (runtimeApiKey != null && !runtimeApiKey.isBlank()) return runtimeProvider;
    if (geminiApiKey != null && !geminiApiKey.isBlank()) return "gemini";
    if (openaiApiKey != null && !openaiApiKey.isBlank()) return "openai";
    return runtimeProvider == null ? "gemini" : runtimeProvider;
  }

  public String model() {
    return "openai".equals(provider()) ? openaiModel : geminiModel;
  }

  private String resolvedApiKey() {
    if (runtimeApiKey != null && !runtimeApiKey.isBlank()) return runtimeApiKey;
    return envApiKeyFor(provider());
  }

  private String envApiKeyFor(String provider) {
    return "openai".equals(provider) ? openaiApiKey : geminiApiKey;
  }

  private String normalizeProvider(String provider) {
    String normalized = provider == null ? "gemini" : provider.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("chatgpt") || normalized.equals("openai")) return "openai";
    if (normalized.equals("gemini") || normalized.equals("google")) return "gemini";
    throw new IllegalArgumentException("지원하지 않는 LLM provider입니다: " + provider);
  }

  public GroundedResponse generateGrounded(String prompt) {
    return "openai".equals(provider()) ? generateOpenAiGrounded(prompt) : generateGeminiGrounded(prompt);
  }

  private GroundedResponse generateGeminiGrounded(String prompt) {
    String key = resolvedApiKey();
    if (key == null || key.isBlank()) return GroundedResponse.notReady("GEMINI_API_KEY 환경변수 또는 관리자 입력 Gemini 키가 설정되지 않았습니다.");
    try {
      String encodedModel = URLEncoder.encode(geminiModel, StandardCharsets.UTF_8);
      URI uri = URI.create(GEMINI_API_BASE + encodedModel + ":generateContent?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8));
      String body = objectMapper.writeValueAsString(Map.of(
          "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
          "tools", List.of(Map.of("google_search", Map.of())),
          "generationConfig", Map.of("temperature", 0.2)
      ));
      HttpResponse<String> response = sendJson(uri, body, null);
      if (response.statusCode() < 200 || response.statusCode() >= 300) return GroundedResponse.failed(String.valueOf(response.statusCode()), parseErrorMessage(response.body()));
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode candidates = root.path("candidates");
      if (!candidates.isArray() || candidates.isEmpty()) return GroundedResponse.failed("NO_CANDIDATES", "Gemini 응답에 candidates가 없습니다.");
      JsonNode first = candidates.get(0);
      StringBuilder text = new StringBuilder();
      for (JsonNode part : first.path("content").path("parts")) text.append(part.path("text").asText(""));
      List<GroundingChunk> chunks = new ArrayList<>();
      for (JsonNode chunk : first.path("groundingMetadata").path("groundingChunks")) {
        JsonNode web = chunk.path("web");
        if (!web.isMissingNode()) chunks.add(new GroundingChunk(web.path("uri").asText(""), web.path("title").asText("")));
      }
      List<String> webQueries = new ArrayList<>();
      for (JsonNode query : first.path("groundingMetadata").path("webSearchQueries")) webQueries.add(query.asText());
      return new GroundedResponse(true, text.toString(), chunks, webQueries, null, null);
    } catch (IOException e) {
      return GroundedResponse.failed("REQUEST_PROCESSING_ERROR", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return GroundedResponse.failed("INTERRUPTED", "Gemini 요청이 중단되었습니다.");
    } catch (RuntimeException e) {
      return GroundedResponse.failed("REQUEST_PROCESSING_ERROR", e.getMessage());
    }
  }

  private GroundedResponse generateOpenAiGrounded(String prompt) {
    String key = resolvedApiKey();
    if (key == null || key.isBlank()) return GroundedResponse.notReady("OPENAI_API_KEY 환경변수 또는 관리자 입력 ChatGPT 키가 설정되지 않았습니다.");
    try {
      String body = objectMapper.writeValueAsString(Map.of(
          "model", openaiModel,
          "input", prompt,
          "tools", List.of(Map.of("type", "web_search")),
          "temperature", 0.2
      ));
      HttpResponse<String> response = sendJson(URI.create(OPENAI_RESPONSES_API), body, "Bearer " + key);
      if (response.statusCode() < 200 || response.statusCode() >= 300) return GroundedResponse.failed(String.valueOf(response.statusCode()), parseErrorMessage(response.body()));
      JsonNode root = objectMapper.readTree(response.body());
      String text = root.path("output_text").asText("");
      if (text.isBlank()) text = collectOpenAiOutputText(root);
      List<GroundingChunk> chunks = collectOpenAiCitations(root);
      List<String> queries = collectOpenAiQueries(root);
      if (text.isBlank()) return GroundedResponse.failed("NO_OUTPUT_TEXT", "OpenAI 응답에서 output_text를 찾지 못했습니다.");
      return new GroundedResponse(true, text, chunks, queries, null, null);
    } catch (IOException e) {
      return GroundedResponse.failed("REQUEST_PROCESSING_ERROR", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return GroundedResponse.failed("INTERRUPTED", "OpenAI 요청이 중단되었습니다.");
    } catch (RuntimeException e) {
      return GroundedResponse.failed("REQUEST_PROCESSING_ERROR", e.getMessage());
    }
  }

  private HttpResponse<String> sendJson(URI uri, String body, String authorization) throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (authorization != null) builder.header("Authorization", authorization);
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private String collectOpenAiOutputText(JsonNode root) {
    StringBuilder text = new StringBuilder();
    for (JsonNode item : root.path("output")) {
      for (JsonNode content : item.path("content")) {
        if ("output_text".equals(content.path("type").asText())) text.append(content.path("text").asText(""));
      }
    }
    return text.toString();
  }

  private List<GroundingChunk> collectOpenAiCitations(JsonNode root) {
    List<GroundingChunk> chunks = new ArrayList<>();
    for (JsonNode item : root.path("output")) {
      for (JsonNode content : item.path("content")) {
        for (JsonNode annotation : content.path("annotations")) {
          String url = annotation.path("url").asText("");
          if (!url.isBlank()) chunks.add(new GroundingChunk(url, annotation.path("title").asText("")));
        }
      }
    }
    return chunks;
  }

  private List<String> collectOpenAiQueries(JsonNode root) {
    List<String> queries = new ArrayList<>();
    for (JsonNode item : root.path("output")) {
      if (item.path("type").asText("").contains("web_search")) {
        String query = item.path("action").path("query").asText("");
        if (!query.isBlank()) queries.add(query);
      }
    }
    return queries;
  }

  private String parseErrorMessage(String body) {
    try { return objectMapper.readTree(body).path("error").path("message").asText("LLM API 요청이 실패했습니다."); }
    catch (Exception ignored) { return "LLM API 요청이 실패했습니다."; }
  }

  public record GroundingChunk(String uri, String title) {}
  public record GroundedResponse(boolean ok, String text, List<GroundingChunk> groundingChunks,
      List<String> webSearchQueries, String errorCode, String errorMessage) {
    static GroundedResponse notReady(String message) { return new GroundedResponse(false, "", List.of(), List.of(), "CONFIGURATION_MISSING", message); }
    static GroundedResponse failed(String code, String message) { return new GroundedResponse(false, "", List.of(), List.of(), code, message); }
  }
}
