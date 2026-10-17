package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * muse-spark-1.3-contributor 함수 호출(tools) 지원 여부 검증.
 *
 * <p>실제 API 키 없이 OpenAI 호환 tools 요청 형식을 검증한다.
 * 실제 API 응답 검증은 운영 키로 별도 확인 필요.
 */
@DisplayName("Muse Spark 1.3 contributor tools 호출 형식 검증")
class MuseSparkToolsCallFormatTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private HttpServer fakeApi;

  @AfterEach
  void stopFakeApi() {
    if (fakeApi != null) fakeApi.stop(0);
  }

  @Test
  @DisplayName("given tools 파라미터 when chat completions 호출 then OpenAI 호환 형식으로 전송된다")
  void toolsRequest_isOpenAiCompatible() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();

    fakeApi = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeApi.createContext("/v1/chat/completions", exchange -> {
      capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      // tool_calls 응답 시뮬레이션
      String response = """
          {
            "choices": [{
              "message": {
                "role": "assistant",
                "content": null,
                "tool_calls": [{
                  "id": "call_123",
                  "type": "function",
                  "function": {
                    "name": "request_card_sales_download",
                    "arguments": "{\\"business_id\\":\\"759-91-01977\\",\\"from_date\\":\\"2026-10-01\\",\\"to_date\\":\\"2026-10-31\\"}"
                  }
                }]
              },
              "finish_reason": "tool_calls"
            }]
          }""";
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (var os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    });
    fakeApi.start();
    String baseUrl = "http://127.0.0.1:" + fakeApi.getAddress().getPort();

    // OpenAI 호환 tools 요청 구성
    Map<String, Object> requestBody = Map.of(
        "model", "muse-spark-1.3-contributor",
        "messages", List.of(Map.of("role", "user", "content", "이번달 카드매출 가져와줘")),
        "tools", List.of(Map.of(
            "type", "function",
            "function", Map.of(
                "name", "request_card_sales_download",
                "description", "여신금융협회에서 카드매출 내역 다운로드 요청",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "business_id", Map.of("type", "string"),
                        "from_date", Map.of("type", "string"),
                        "to_date", Map.of("type", "string")),
                    "required", List.of("business_id", "from_date", "to_date"))))),
        "tool_choice", "auto",
        "max_tokens", 8000);

    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/v1/chat/completions"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer test-key-not-real")
        .POST(HttpRequest.BodyPublishers.ofString(
            MAPPER.writeValueAsString(requestBody), StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> response = client.send(request,
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    // then: 요청이 OpenAI 호환 형식으로 전송됨
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode sentBody = MAPPER.readTree(capturedBody.get());
    assertThat(sentBody.get("model").asText()).isEqualTo("muse-spark-1.3-contributor");
    assertThat(sentBody.get("tools").isArray()).isTrue();
    assertThat(sentBody.get("tools").get(0).get("type").asText()).isEqualTo("function");
    assertThat(sentBody.get("tools").get(0).get("function").get("name").asText())
        .isEqualTo("request_card_sales_download");
    assertThat(sentBody.get("tool_choice").asText()).isEqualTo("auto");

    // then: tool_calls 응답 파싱 가능
    JsonNode responseBody = MAPPER.readTree(response.body());
    JsonNode toolCalls = responseBody.get("choices").get(0).get("message").get("tool_calls");
    assertThat(toolCalls.isArray()).isTrue();
    assertThat(toolCalls.get(0).get("function").get("name").asText())
        .isEqualTo("request_card_sales_download");
    JsonNode args = MAPPER.readTree(toolCalls.get(0).get("function").get("arguments").asText());
    assertThat(args.get("business_id").asText()).isEqualTo("759-91-01977");
  }
}
