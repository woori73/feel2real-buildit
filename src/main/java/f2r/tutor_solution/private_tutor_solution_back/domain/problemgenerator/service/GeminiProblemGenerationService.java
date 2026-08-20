package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class GeminiProblemGenerationService {
  public static final String MODEL = "gemini-3.5-flash";
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public GeminiProblemGenerationService() {
    this.objectMapper = new ObjectMapper();
    this.restClient = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").build();
  }

  public String generate(String apiKey, String grammar, String passage, int count, String difficulty, String materialTitle) {
    String resolvedApiKey = resolveApiKey(apiKey);
    if ((grammar == null || grammar.isBlank()) && (passage == null || passage.isBlank())) {
      throw new IllegalArgumentException("핵심 문법 또는 새 지문을 입력해 주세요.");
    }
    Map<String, Object> request = Map.of(
        "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction()))),
        "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt(grammar, passage, count, difficulty, materialTitle))))),
        "generationConfig", Map.of("temperature", 0.35, "responseMimeType", "application/json"));
    try {
      String response = restClient.post().uri("/v1beta/models/{model}:generateContent", MODEL)
          .header("x-goog-api-key", resolvedApiKey).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
          .body(request).retrieve().body(String.class);
      return extractText(parseResponse(response));
    } catch (HttpStatusCodeException exception) {
      throw new IllegalStateException("Gemini API 호출 실패: HTTP " + exception.getStatusCode().value() + geminiErrorMessage(exception));
    }
  }

  public String chat(String apiKey, String message, String context) {
    String resolvedApiKey = resolveApiKey(apiKey);
    if (message == null || message.isBlank()) throw new IllegalArgumentException("질문을 입력해 주세요.");
    String prompt = "현재 작업 컨텍스트:\n" + nullToBlank(context) + "\n\n사용자 질문:\n" + message.trim();
    Map<String, Object> request = Map.of(
        "systemInstruction", Map.of("parts", List.of(Map.of("text", assistantInstruction()))),
        "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
        "generationConfig", Map.of("temperature", 0.4));
    try {
      String response = restClient.post().uri("/v1beta/models/{model}:generateContent", MODEL)
          .header("x-goog-api-key", resolvedApiKey).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
          .body(request).retrieve().body(String.class);
      return extractText(parseResponse(response));
    } catch (HttpStatusCodeException exception) {
      throw new IllegalStateException("Gemini API 호출 실패: HTTP " + exception.getStatusCode().value() + geminiErrorMessage(exception));
    } catch (RestClientException exception) {
      throw new IllegalStateException("Gemini API 네트워크 호출 실패: " + exception.getMessage());
    }
  }

  private String resolveApiKey(String requestApiKey) {
    if (requestApiKey != null && !requestApiKey.isBlank()) return requestApiKey.trim();
    String serverKey = System.getenv("GEMINI_API_KEY");
    if (serverKey != null && !serverKey.isBlank()) return serverKey.trim();
    throw new IllegalArgumentException("Gemini API 키가 설정되지 않았습니다. 서버 환경변수 GEMINI_API_KEY를 등록해 주세요.");
  }

  private JsonNode parseResponse(String response) {
    if (response == null || response.isBlank()) throw new IllegalStateException("Gemini 응답이 비어 있습니다.");
    try { return objectMapper.readTree(response); }
    catch (JsonProcessingException exception) { throw new IllegalStateException("Gemini 응답 JSON 파싱 실패: " + exception.getOriginalMessage()); }
  }

  private String extractText(JsonNode response) {
    JsonNode parts = response == null ? null : response.at("/candidates/0/content/parts");
    if (parts == null || !parts.isArray()) throw new IllegalStateException("Gemini 응답에서 생성 결과를 찾지 못했습니다.");
    StringBuilder builder = new StringBuilder();
    for (JsonNode part : parts) { String text = part.path("text").asText(""); if (!text.isBlank()) builder.append(text); }
    if (builder.isEmpty()) throw new IllegalStateException("Gemini 응답이 비어 있습니다.");
    return builder.toString();
  }

  private String geminiErrorMessage(HttpStatusCodeException exception) {
    String body = exception.getResponseBodyAsString();
    if (body == null || body.isBlank()) return "";
    String compact = body.replaceAll("\\s+", " ").trim();
    if (compact.length() > 220) compact = compact.substring(0, 220) + "...";
    return " - " + compact;
  }

  private String assistantInstruction() {
    return "너는 박원장 AI라는 중등 영어 내신 문제 제작 보조자다. 답변은 한국어로 짧고 실무적으로 한다. 출제 유형, 난이도, 문항 구성, 지문 개선, 채점 기준을 도와준다. API 키나 민감정보를 반복 출력하지 않는다.";
  }

  private String systemInstruction() {
    return "너는 중학교 내신 영어 출제자다. 반드시 JSON만 반환한다. schema: {questions:[{type,level,score,stem,choices,answer,explanation}], summary}. 한국어 발문과 해설을 쓰고, choices는 객관식일 때 5개로 작성한다.";
  }

  private String userPrompt(String grammar, String passage, int count, String difficulty, String materialTitle) {
    return "중등 영어 내신 문제를 생성해 주세요.\n문항 수: " + Math.max(1, Math.min(count, 10))
        + "\n난이도: " + (difficulty == null || difficulty.isBlank() ? "학교 평균" : difficulty)
        + "\n선택 Digital Twin 자료: " + (materialTitle == null || materialTitle.isBlank() ? "미선택" : materialTitle.trim())
        + "\n핵심 문법:\n" + nullToBlank(grammar) + "\n\n새 지문/출제 조건:\n" + nullToBlank(passage) + "\n";
  }

  private String nullToBlank(String value) { return value == null ? "" : value; }
}
