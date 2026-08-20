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
public class LunaProblemTransformationService {
  public static final String MODEL = "gpt-5.6-luna";
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public LunaProblemTransformationService() {
    this.objectMapper = new ObjectMapper();
    this.restClient = RestClient.builder().baseUrl("https://api.openai.com").build();
  }

  public String transform(String sourceQuestion, String instructions, String difficulty, String digitalTwinContext) {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY 서버 환경변수가 설정되지 않았습니다.");
    }
    if (sourceQuestion == null || sourceQuestion.isBlank()) {
      throw new IllegalArgumentException("변형할 기존 문제를 입력해 주세요.");
    }

    Map<String, Object> request = Map.of(
        "model", MODEL,
        "input", List.of(Map.of(
            "role", "user",
            "content", List.of(Map.of("type", "input_text", "text", prompt(sourceQuestion, instructions, difficulty, digitalTwinContext))))),
        "reasoning", Map.of("effort", "low"),
        "text", Map.of("format", Map.of("type", "json_object")));

    try {
      String response = restClient.post()
          .uri("/v1/responses")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
          .body(request)
          .retrieve()
          .body(String.class);
      return extractOutputText(parse(response));
    } catch (HttpStatusCodeException exception) {
      throw new IllegalStateException("OpenAI API 호출 실패: HTTP " + exception.getStatusCode().value() + apiError(exception));
    } catch (RestClientException exception) {
      throw new IllegalStateException("OpenAI API 네트워크 호출 실패: " + exception.getMessage());
    }
  }

  private String prompt(String sourceQuestion, String instructions, String difficulty, String digitalTwinContext) {
    return "너는 BuildIt Wiki의 중학교 영어 내신 문제 변형 전문가다. 반드시 JSON만 반환한다.\n"
        + "schema: {questions:[{type,level,score,stem,choices,answer,explanation}], summary}.\n"
        + "원문 문제의 학습목표와 정답 논리는 유지하되 문장, 선택지, 함정, 난이도를 새롭게 구성한다.\n"
        + "Digital Twin 맥락이 있으면 해당 학교 출제 특성을 우선 반영한다.\n\n"
        + "난이도: " + blank(difficulty, "학교 평균") + "\n"
        + "추가 지시: " + blank(instructions, "없음") + "\n"
        + "Digital Twin: " + blank(digitalTwinContext, "미선택") + "\n\n"
        + "변형할 기존 문제:\n" + sourceQuestion.trim();
  }

  private JsonNode parse(String response) {
    if (response == null || response.isBlank()) throw new IllegalStateException("OpenAI 응답이 비어 있습니다.");
    try { return objectMapper.readTree(response); }
    catch (JsonProcessingException e) { throw new IllegalStateException("OpenAI 응답 JSON 파싱 실패: " + e.getOriginalMessage()); }
  }

  private String extractOutputText(JsonNode root) {
    JsonNode output = root.path("output");
    if (output.isArray()) {
      for (JsonNode item : output) {
        JsonNode content = item.path("content");
        if (!content.isArray()) continue;
        for (JsonNode part : content) {
          if ("output_text".equals(part.path("type").asText()) && !part.path("text").asText("").isBlank()) {
            return part.path("text").asText();
          }
        }
      }
    }
    throw new IllegalStateException("OpenAI 응답에서 변형 결과를 찾지 못했습니다.");
  }

  private String apiError(HttpStatusCodeException exception) {
    String body = exception.getResponseBodyAsString();
    if (body == null || body.isBlank()) return "";
    String compact = body.replaceAll("\\s+", " ").trim();
    if (compact.length() > 220) compact = compact.substring(0, 220) + "...";
    return " - " + compact;
  }

  private String blank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
