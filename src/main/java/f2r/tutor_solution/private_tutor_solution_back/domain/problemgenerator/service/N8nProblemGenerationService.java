package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * BuildIt Wiki 문제 생성을 n8n Production Webhook(Webhook → AI Agent → Gemini 3.5 Flash →
 * Structured Output Parser → Respond to Webhook)에 위임한다.
 *
 * <p>브라우저는 더 이상 Gemini API 키를 다루지 않는다. Spring 서버가 n8n Webhook을 호출하고,
 * n8n의 응답 스키마({@code {school,grade,exam,questions:[{number,type,question,choices,answer,
 * explanation,difficulty,grammar}]}})를 기존 BuildIt 문제 형식({@code {summary,questions:[{type,
 * level,score,stem,choices,answer,explanation}]}})으로 변환해 반환한다. 이렇게 하면 프론트엔드
 * ({@code renderGenerated()})와 저장 로직({@link ProblemGeneratorQuestionSetService#save}) 모두
 * 기존 그대로 동작한다. n8n Webhook 주소는 이 서비스 안에서만 사용되며 브라우저로 노출되지 않는다.
 */
@Service
public class N8nProblemGenerationService {
  /** 화면에 노출되는 모델 표기. n8n 워크플로우가 내부적으로 Gemini 3.5 Flash를 호출한다. */
  public static final String MODEL = "gemini-3.5-flash";

  private static final int MIN_COUNT = 1;
  private static final int MAX_COUNT = 10;
  private static final String DEFAULT_DIFFICULTY = "학교 평균";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String webhookUrl;
  private final HttpClient httpClient;
  private final Duration readTimeout;

  public N8nProblemGenerationService(
      @Value("${app.problem-generator.n8n.webhook-url:}") String webhookUrl,
      @Value("${app.problem-generator.n8n.connect-timeout-ms:5000}") long connectTimeoutMs,
      @Value("${app.problem-generator.n8n.read-timeout-ms:60000}") long readTimeoutMs) {
    this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
        .build();
    this.readTimeout = Duration.ofMillis(Math.max(1000, readTimeoutMs));
  }

  /**
   * @return 기존 Gemini 직접 호출 시절과 동일한 형태({@code {summary,questions:[...]}})의 JSON 문자열
   */
  public String generate(String school, String grade, String exam, String grammar, String passage,
      Integer count, String difficulty) {
    if (isBlank(grammar) && isBlank(passage)) {
      throw new IllegalArgumentException("핵심 문법 또는 새 지문을 입력해 주세요.");
    }
    if (webhookUrl.isBlank()) {
      throw new IllegalStateException(
          "n8n Webhook 주소가 설정되지 않았습니다. app.problem-generator.n8n.webhook-url(또는 "
              + "N8N_BUILDIT_WEBHOOK_URL 환경변수)을 확인해 주세요.");
    }

    int resolvedCount = clampCount(count);
    String resolvedDifficulty = isBlank(difficulty) ? DEFAULT_DIFFICULTY : difficulty.trim();
    String resolvedSchool = blank(school);
    String resolvedGrade = blank(grade);
    String resolvedExam = blank(exam);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("school", resolvedSchool);
    requestBody.put("grade", resolvedGrade);
    requestBody.put("exam", resolvedExam);
    requestBody.put("passage", blank(passage));
    requestBody.put("grammar", blank(grammar));
    requestBody.put("count", resolvedCount);
    requestBody.put("difficulty", resolvedDifficulty);

    String responseBody = callWebhook(writeRequestJson(requestBody));
    JsonNode n8nResponse = parseResponse(responseBody);
    return adapt(resolvedSchool, resolvedGrade, resolvedExam, n8nResponse);
  }

  private String writeRequestJson(Map<String, Object> requestBody) {
    try {
      return objectMapper.writeValueAsString(requestBody);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("n8n 요청 직렬화 실패: " + exception.getOriginalMessage());
    }
  }

  private String callWebhook(String requestJson) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
        .timeout(readTimeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
        .build();
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (HttpTimeoutException exception) {
      throw new IllegalStateException("n8n Webhook 응답이 시간 내에 오지 않았습니다(타임아웃): " + exception.getMessage());
    } catch (IOException exception) {
      throw new IllegalStateException("n8n Webhook에 연결할 수 없습니다(네트워크 오류): " + exception.getMessage());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("n8n Webhook 호출이 중단되었습니다.");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException(
          "n8n Webhook 호출 실패: HTTP " + response.statusCode() + errorSnippet(response.body()));
    }
    return response.body();
  }

  JsonNode parseResponse(String response) {
    if (response == null || response.isBlank()) {
      throw new IllegalStateException("n8n 응답이 비어 있습니다.");
    }
    try {
      JsonNode root = objectMapper.readTree(response);
      JsonNode output = root.path("output");
      if (output.isObject()) return output;
      if (output.isTextual() && !output.asText().isBlank()) {
        String unwrapped = output.asText().replaceFirst("(?s)^\\s*```(?:json)?\\s*", "")
            .replaceFirst("(?s)\\s*```\\s*$", "");
        return objectMapper.readTree(unwrapped);
      }
      return root;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("n8n 응답 JSON 파싱 실패: " + exception.getOriginalMessage());
    }
  }

  /** n8n 응답({@code {school,grade,exam,questions[]}})을 기존 BuildIt 문제 형식 JSON 문자열로 변환한다. */
  String adapt(String requestSchool, String requestGrade, String requestExam, JsonNode n8nResponse) {
    JsonNode questions = n8nResponse.path("questions");
    if (!questions.isArray() || questions.isEmpty()) {
      throw new IllegalStateException("n8n 응답에 questions 배열이 없습니다.");
    }

    String resolvedSchool = firstNonBlank(n8nResponse.path("school").asText(null), requestSchool);
    String resolvedGrade = firstNonBlank(n8nResponse.path("grade").asText(null), requestGrade);
    String resolvedExam = firstNonBlank(n8nResponse.path("exam").asText(null), requestExam);

    ObjectNode root = objectMapper.createObjectNode();
    root.put("summary", summaryText(resolvedSchool, resolvedGrade, resolvedExam, questions.size()));
    ArrayNode outQuestions = root.putArray("questions");
    for (JsonNode question : questions) {
      outQuestions.add(adaptQuestion(question));
    }

    try {
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("n8n 응답 변환 결과 직렬화 실패: " + exception.getOriginalMessage());
    }
  }

  private ObjectNode adaptQuestion(JsonNode question) {
    ObjectNode out = objectMapper.createObjectNode();
    out.put("type", question.path("type").asText(""));
    out.put("stem", question.path("question").asText(""));
    out.put("answer", question.path("answer").asText(""));
    out.put("explanation", question.path("explanation").asText(""));
    String level = question.path("difficulty").asText("");
    if (!level.isBlank()) out.put("level", level);
    String grammarTag = question.path("grammar").asText("");
    if (!grammarTag.isBlank()) out.put("grammar", grammarTag);
    JsonNode choices = question.path("choices");
    if (choices.isArray() && !choices.isEmpty()) {
      ArrayNode outChoices = out.putArray("choices");
      for (JsonNode choice : choices) outChoices.add(choice.asText(""));
    }
    return out;
  }

  private String summaryText(String school, String grade, String exam, int count) {
    StringBuilder builder = new StringBuilder();
    for (String part : new String[] {school, grade, exam}) {
      if (!part.isBlank()) builder.append(part).append(" ");
    }
    builder.append("문제 ").append(count).append("문항 생성 완료 (n8n)");
    return builder.toString().trim();
  }

  private int clampCount(Integer count) {
    int value = count == null ? 3 : count;
    return Math.max(MIN_COUNT, Math.min(value, MAX_COUNT));
  }

  private String errorSnippet(String body) {
    if (body == null || body.isBlank()) return "";
    String compact = body.replaceAll("\\s+", " ").trim();
    if (compact.length() > 220) compact = compact.substring(0, 220) + "...";
    return " - " + compact;
  }

  private String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) return primary.trim();
    return blank(fallback);
  }

  private String blank(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
