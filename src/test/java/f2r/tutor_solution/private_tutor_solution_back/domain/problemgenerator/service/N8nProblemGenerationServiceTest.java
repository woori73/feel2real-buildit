package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class N8nProblemGenerationServiceTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("given blank grammar and passage when generate then validation fails before calling n8n")
  void generate_requiresGrammarOrPassage() {
    N8nProblemGenerationService service = service("https://n8n.example.com/webhook/buildit-question-generate");

    assertThatThrownBy(() -> service.generate("신사중", "중2", "1학기 중간", "", "", 3, "학교 평균"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("핵심 문법 또는 새 지문을 입력해 주세요");
  }

  @Test
  @DisplayName("given no webhook url configured when generate then a clear configuration error is thrown")
  void generate_requiresWebhookUrlConfigured() {
    N8nProblemGenerationService service = service("");

    assertThatThrownBy(() -> service.generate("신사중", "중2", "1학기 중간", "관계대명사", null, 3, "학교 평균"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("n8n Webhook 주소가 설정되지 않았습니다");
  }

  @Test
  @DisplayName("given n8n response with questions[] when adapt then it is converted to the legacy BuildIt schema")
  void adapt_convertsN8nResponseToLegacySchema() throws Exception {
    N8nProblemGenerationService service = service("https://n8n.example.com/webhook/buildit-question-generate");
    JsonNode n8nResponse = objectMapper.readTree("""
        {
          "school": "신사중",
          "grade": "중2",
          "exam": "1학기 중간",
          "questions": [
            {
              "number": 1,
              "type": "객관식",
              "question": "다음 빈칸에 알맞은 것은?",
              "choices": ["A", "B", "C", "D", "E"],
              "answer": "B",
              "explanation": "관계대명사 what은 선행사를 포함한다.",
              "difficulty": "중",
              "grammar": "관계대명사 what"
            },
            {
              "number": 2,
              "type": "서술형",
              "question": "다음 문장을 해석하시오.",
              "choices": [],
              "answer": "나는 그것을 안다.",
              "explanation": "직역하면 된다.",
              "difficulty": "하",
              "grammar": "현재완료"
            }
          ]
        }
        """);

    String adapted = service.adapt("신사중", "중2", "1학기 중간", n8nResponse);
    JsonNode root = objectMapper.readTree(adapted);

    assertThat(root.path("summary").asText()).contains("신사중").contains("중2").contains("1학기 중간").contains("2문항");
    assertThat(root.path("questions")).hasSize(2);
    JsonNode first = root.path("questions").get(0);
    assertThat(first.path("type").asText()).isEqualTo("객관식");
    assertThat(first.path("stem").asText()).isEqualTo("다음 빈칸에 알맞은 것은?");
    assertThat(first.path("answer").asText()).isEqualTo("B");
    assertThat(first.path("explanation").asText()).isEqualTo("관계대명사 what은 선행사를 포함한다.");
    assertThat(first.path("level").asText()).isEqualTo("중");
    assertThat(first.path("grammar").asText()).isEqualTo("관계대명사 what");
    assertThat(first.path("choices")).hasSize(5);
    JsonNode second = root.path("questions").get(1);
    assertThat(second.path("choices").isMissingNode() || second.path("choices").isArray() && second.path("choices").isEmpty()).isTrue();
  }

  @Test
  @DisplayName("given school/grade/exam missing from n8n response when adapt then request values are used as fallback")
  void adapt_fallsBackToRequestFieldsWhenN8nOmitsThem() throws Exception {
    N8nProblemGenerationService service = service("https://n8n.example.com/webhook/buildit-question-generate");
    JsonNode n8nResponse = objectMapper.readTree("""
        {"questions":[{"type":"객관식","question":"Q","choices":[],"answer":"A","explanation":"E"}]}
        """);

    String adapted = service.adapt("신사중", "중2", "1학기 중간", n8nResponse);
    JsonNode root = objectMapper.readTree(adapted);

    assertThat(root.path("summary").asText()).contains("신사중").contains("중2").contains("1학기 중간");
  }

  @Test
  @DisplayName("given empty questions array when adapt then a clear error is thrown instead of saving nothing")
  void adapt_rejectsEmptyQuestions() throws Exception {
    N8nProblemGenerationService service = service("https://n8n.example.com/webhook/buildit-question-generate");
    JsonNode n8nResponse = objectMapper.readTree("{\"questions\":[]}");

    assertThatThrownBy(() -> service.adapt("신사중", "중2", "1학기 중간", n8nResponse))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("questions 배열이 없습니다");
  }

  @Test
  @DisplayName("given a non-JSON n8n response body when parseResponse then a clear parse error is thrown")
  void parseResponse_rejectsInvalidJson() {
    N8nProblemGenerationService service = service("https://n8n.example.com/webhook/buildit-question-generate");

    assertThatThrownBy(() -> service.parseResponse("not-json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("n8n 응답 JSON 파싱 실패");
  }

  @Test
  @DisplayName("given an empty n8n response body when parseResponse then a clear empty-response error is thrown")
  void parseResponse_rejectsBlankBody() {
    N8nProblemGenerationService service = service("https://n8n.example.com/webhook/buildit-question-generate");

    assertThatThrownBy(() -> service.parseResponse(""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("n8n 응답이 비어 있습니다");
  }

  @Test
  @DisplayName("given AI Agent output wrapper when parseResponse then structured JSON is unwrapped")
  void parseResponse_unwrapsAgentOutput() {
    N8nProblemGenerationService service = service("https://n8n.example.com/webhook/buildit-question-generate");

    JsonNode parsed = service.parseResponse(
        "{\"output\":\"```json\\n{\\\"school\\\":\\\"신사중\\\",\\\"questions\\\":[{\\\"question\\\":\\\"Q\\\"}]}\\n```\"}");

    assertThat(parsed.path("school").asText()).isEqualTo("신사중");
    assertThat(parsed.path("questions")).hasSize(1);
  }

  @Test
  @DisplayName("given object output wrapper when parseResponse then object is unwrapped")
  void parseResponse_unwrapsObjectOutput() {
    N8nProblemGenerationService service = service("https://n8n.example.com/webhook/buildit-question-generate");

    JsonNode parsed = service.parseResponse(
        "{\"output\":{\"questions\":[{\"question\":\"Q\"}]}}");

    assertThat(parsed.path("questions")).hasSize(1);
  }

  private N8nProblemGenerationService service(String webhookUrl) {
    return new N8nProblemGenerationService(webhookUrl, 5000, 60000);
  }
}
