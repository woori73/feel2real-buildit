package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MuseSparkToolsService — P1 함수 호출 연동 테스트.
 *
 * <p>실제 API 키 없이 mock 서버로 검증한다.
 * 실제 API 호출 검증은 운영 키로 별도 확인 필요 (미검증).
 */
@DisplayName("MuseSparkToolsService — P1 함수 호출")
class MuseSparkToolsServiceTest {

  private HttpServer fakeApi;

  @AfterEach
  void stopFakeApi() {
    if (fakeApi != null) {
      fakeApi.stop(0);
    }
  }

  private MuseSparkToolsService service(String baseUrl) {
    NewStartLlmProperties properties = new NewStartLlmProperties();
    properties.getMeta().setBaseUrl(baseUrl);
    properties.getMeta().setApiKey("test-key-not-real");
    return new MuseSparkToolsService(properties);
  }

  private String startFakeApi(String responseBody, AtomicReference<String> capturedBody)
      throws IOException {
    fakeApi = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeApi.createContext("/chat/completions", exchange -> {
      capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (var os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    });
    fakeApi.start();
    return "http://127.0.0.1:" + fakeApi.getAddress().getPort();
  }

  private static final String TOOL_CALL_RESPONSE = """
      {
        "choices": [{
          "message": {
            "role": "assistant",
            "content": null,
            "tool_calls": [{
              "id": "call_abc123",
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

  private static final String TEXT_ONLY_RESPONSE = """
      {
        "choices": [{
          "message": {
            "role": "assistant",
            "content": "안녕하세요. 무엇을 도와드릴까요?"
          },
          "finish_reason": "stop"
        }]
      }""";

  private static final String BLOCKED_FUNCTION_RESPONSE = """
      {
        "choices": [{
          "message": {
            "role": "assistant",
            "content": null,
            "tool_calls": [{
              "id": "call_evil",
              "type": "function",
              "function": {
                "name": "delete_all_data",
                "arguments": "{}"
              }
            }]
          },
          "finish_reason": "tool_calls"
        }]
      }""";

  @Test
  @DisplayName("given tools 요청 when API가 tool_calls 반환 then 함수 호출 파싱된다")
  void completeWithTools_parsesToolCalls() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    MuseSparkToolsService svc = service(startFakeApi(TOOL_CALL_RESPONSE, captured));

    MuseSparkToolsService.MuseSparkToolsResult result =
        svc.completeWithTools("system", "카드매출 가져와줘");

    assertThat(result.isOk()).isTrue();
    assertThat(result.hasToolCalls()).isTrue();
    assertThat(result.getToolCalls()).hasSize(1);
    assertThat(result.getToolCalls().get(0).name()).isEqualTo("request_card_sales_download");
    assertThat(result.getToolCalls().get(0).arguments()).containsKey("business_id");
    // 요청에 tools와 tool_choice가 포함됨
    assertThat(captured.get()).contains("\"tools\"");
    assertThat(captured.get()).contains("\"tool_choice\":\"auto\"");
  }

  @Test
  @DisplayName("given 함수 호출 없음 when API가 텍스트만 반환 then 빈 호출 목록으로 처리된다")
  void completeWithTools_textOnly_noToolCalls() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    MuseSparkToolsService svc = service(startFakeApi(TEXT_ONLY_RESPONSE, captured));

    MuseSparkToolsService.MuseSparkToolsResult result =
        svc.completeWithTools("system", "안녕");

    assertThat(result.isOk()).isTrue();
    assertThat(result.hasToolCalls()).isFalse();
    assertThat(result.getTextContent()).contains("안녕하세요");
  }

  @Test
  @DisplayName("given 화이트리스트 외 함수 when API가 반환 then 차단된다")
  void completeWithTools_nonWhitelistedFunction_blocked() throws Exception {
    AtomicReference<String> captured = new AtomicReference<>();
    MuseSparkToolsService svc = service(startFakeApi(BLOCKED_FUNCTION_RESPONSE, captured));

    MuseSparkToolsService.MuseSparkToolsResult result =
        svc.completeWithTools("system", "test");

    assertThat(result.isOk()).isFalse();
    assertThat(result.getError()).contains("허용되지 않은 함수");
  }

  @Test
  @DisplayName("given 필수 인자 누락 when 검증 then 실패한다")
  void validateArguments_missingRequired_returnsFalse() {
    NewStartLlmProperties properties = new NewStartLlmProperties();
    properties.getMeta().setApiKey("test");
    MuseSparkToolsService svc = new MuseSparkToolsService(properties);

    assertThat(svc.validateArguments("request_card_sales_download",
        "{\"business_id\":\"123\",\"from_date\":\"2026-10-01\"}")).isFalse();
    assertThat(svc.validateArguments("request_card_sales_download",
        "{\"business_id\":\"123\",\"from_date\":\"2026-10-01\",\"to_date\":\"2026-10-31\"}")).isTrue();
    assertThat(svc.validateArguments("check_download_status", "{\"job_id\":\"abc\"}")).isTrue();
    assertThat(svc.validateArguments("check_download_status", "{}")).isFalse();
  }

  @Test
  @DisplayName("given API 키 미설정 when 호출 then 실패한다")
  void completeWithTools_noApiKey_failed() {
    NewStartLlmProperties properties = new NewStartLlmProperties();
    properties.getMeta().setApiKey("");
    MuseSparkToolsService svc = new MuseSparkToolsService(properties);

    MuseSparkToolsService.MuseSparkToolsResult result =
        svc.completeWithTools("system", "test");

    assertThat(result.isOk()).isFalse();
  }
}
