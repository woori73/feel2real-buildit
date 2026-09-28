package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OpenRouterQwenLlmTransport — NewStart 기술적 실패 전용 DIRECT fallback. 기존 공용 Wiki Router의
 * Qwen 경로와 동일한 요청 계약(model·messages·temperature)을 라우터 없이 붙이고, primary와 달리
 * 자기 실패로는 절대 추가 호출을 유발하지 않는 것을 로컬 fake 서버로 검증한다.
 */
@DisplayName("OpenRouterQwenLlmTransport — Qwen DIRECT fallback 계약")
class OpenRouterQwenLlmTransportTest {

  private static final String KEY = "or-test-key-never-printed";

  private HttpServer fakeOpenRouter;

  @AfterEach
  void stopFakeOpenRouter() {
    if (fakeOpenRouter != null) fakeOpenRouter.stop(0);
  }

  private OpenRouterQwenLlmTransport transport(String baseUrl, String apiKey) {
    NewStartLlmProperties properties = new NewStartLlmProperties();
    properties.getOpenrouter().setBaseUrl(baseUrl);
    properties.getOpenrouter().setApiKey(apiKey);
    return new OpenRouterQwenLlmTransport(properties);
  }

  private String startFakeOpenRouter(int status, String responseBody, AtomicInteger hits,
      AtomicReference<String> capturedBody) throws IOException {
    fakeOpenRouter = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeOpenRouter.createContext("/chat/completions", exchange -> {
      hits.incrementAndGet();
      capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, response.length);
      try (var os = exchange.getResponseBody()) {
        os.write(response);
      }
    });
    fakeOpenRouter.start();
    return "http://127.0.0.1:" + fakeOpenRouter.getAddress().getPort();
  }

  @Test
  @DisplayName("given 정상 응답 when complete then Qwen 모델로 기존 라우터와 같은 body 계약 호출하고 content만 반환한다")
  void complete_callsQwenWithRouterEquivalentBody() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    AtomicReference<String> capturedBody = new AtomicReference<>();
    String baseUrl = startFakeOpenRouter(200, "{\"model\":\"qwen/qwen3.7-flash\","
        + "\"choices\":[{\"message\":{\"content\":\"폴백 답변\",\"reasoning_content\":\"노출 금지\"}}]}",
        hits, capturedBody);

    NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isTrue();
    assertThat(result.text()).isEqualTo("폴백 답변");
    assertThat(result.text()).doesNotContain("노출 금지");
    assertThat(result.model()).isEqualTo("qwen/qwen3.7-flash");
    assertThat(hits.get()).isEqualTo(1);
    assertThat(capturedBody.get())
        .contains("\"model\":\"qwen/qwen3.7-flash\"")
        .contains("\"temperature\":0.25")
        .contains("\"content\":\"시스템\"")
        .contains("\"content\":\"질문\"");
    assertThat(capturedBody.get()).doesNotContain(KEY);
  }

  @Test
  @DisplayName("given HTTP 오류·빈 content·연결 실패 when complete then fallbackEligible=false로 통제된 실패만 보고한다")
  void complete_failuresAreNeverEligible() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    AtomicReference<String> capturedBody = new AtomicReference<>();
    String[] bodies = {"{\"detail\":\"nope\"}", "{\"choices\":[]}",
        "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"};
    for (String body : bodies) {
      String baseUrl = startFakeOpenRouter(500, body, hits, capturedBody);

      NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

      assertThat(result.ok()).isFalse();
      assertThat(result.fallbackEligible()).isFalse();
      fakeOpenRouter.stop(0);
      fakeOpenRouter = null;
    }

    NewStartLlmCompletion connectionFailure =
        transport("http://127.0.0.1:1", KEY).complete("시스템", "질문", 4000);
    assertThat(connectionFailure.ok()).isFalse();
    assertThat(connectionFailure.fallbackEligible()).isFalse();
  }

  @Test
  @DisplayName("given OPENROUTER_API_KEY 미설정 when complete then 네트워크를 타지 않고 실패한다")
  void complete_withoutKeyDoesNotCallNetwork() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    AtomicReference<String> capturedBody = new AtomicReference<>();
    String baseUrl = startFakeOpenRouter(200, "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}",
        hits, capturedBody);

    NewStartLlmCompletion result = transport(baseUrl, "").complete("시스템", "질문", 4000);

    assertThat(result.ok()).isFalse();
    assertThat(result.fallbackEligible()).isFalse();
    assertThat(hits.get()).isZero();
  }
}
