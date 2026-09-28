package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NvidiaNimLlmTransport — NewStart 지피지기 primary. 실제 NIM 대신 로컬 fake HTTP 서버로
 * (1) 공식 sample 계약(temperature=1·top_p=0.95·max_tokens=16384·chat_template_kwargs.enable_thinking·
 * reasoning_budget=16384), (2) assistant content만 반환(reasoning_content 미노출),
 * (3) §4 기술적 실패만 fallbackEligible=true 판정을 검증한다.
 */
@DisplayName("NvidiaNimLlmTransport — NVIDIA NIM Lightning DIRECT 공식 계약·실패 분류")
class NvidiaNimLlmTransportTest {

  private static final String KEY = "nv-test-key-never-printed";

  private HttpServer fakeNim;

  @AfterEach
  void stopFakeNim() {
    if (fakeNim != null) fakeNim.stop(0);
  }

  /** given fake NIM 응답 when complete then 로 분류할 결과를 만들어주는 client. */
  private NvidiaNimLlmTransport transport(String baseUrl, String apiKey) {
    NewStartLlmProperties properties = new NewStartLlmProperties();
    properties.getNvidia().setBaseUrl(baseUrl);
    properties.getNvidia().setApiKey(apiKey);
    return new NvidiaNimLlmTransport(properties);
  }

  private String startFakeNim(int status, Supplier<String> responseBody,
      AtomicReference<String> capturedBody, AtomicReference<String> capturedAuth) throws IOException {
    fakeNim = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeNim.createContext("/chat/completions", exchange -> {
      capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, response.length);
      try (var os = exchange.getResponseBody()) {
        os.write(response);
      }
    });
    fakeNim.start();
    return "http://127.0.0.1:" + fakeNim.getAddress().getPort();
  }

  @Test
  @DisplayName("given 정상 응답 when complete then 공식 sample 계약 body로 호출하고 assistant content만 돌려준다")
  void complete_sendsOfficialSampleContractAndReturnsContentOnly() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();
    String baseUrl = startFakeNim(200, () -> "{\"model\":\"nvidia/nemotron-3.5-lightning-30b-a3b\","
        + "\"provider\":\"nvidia\","
        + "\"choices\":[{\"message\":{\"content\":\"최종 답변\",\"reasoning_content\":\"내부 사고 노출 금지\"},"
        + "\"finish_reason\":\"stop\"}]}", capturedBody, capturedAuth);

    NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isTrue();
    assertThat(result.text()).isEqualTo("최종 답변");
    assertThat(result.text()).doesNotContain("내부 사고");
    assertThat(result.provider()).isEqualTo("nvidia");
    assertThat(result.model()).isEqualTo("nvidia/nemotron-3.5-lightning-30b-a3b");
    assertThat(capturedAuth.get()).isEqualTo("Bearer " + KEY);
    assertThat(capturedBody.get())
        .contains("\"model\":\"nvidia/nemotron-3.5-lightning-30b-a3b\"")
        .contains("\"temperature\":1.0")
        .contains("\"top_p\":0.95")
        .contains("\"max_tokens\":16384")
        .contains("\"chat_template_kwargs\":{\"enable_thinking\":true}")
        .contains("\"reasoning_budget\":16384")
        .contains("\"content\":\"시스템\"")
        .contains("\"content\":\"질문\"");
    // 키 값은 요청 헤더에만 실리고 body·로그·결과에 복제되지 않는다.
    assertThat(capturedBody.get()).doesNotContain(KEY);
  }

  @Test
  @DisplayName("given content가 text part 배열 when complete then 이어 붙여 최종 답변으로 반환한다")
  void complete_joinsTextPartArray() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();
    String baseUrl = startFakeNim(200, () -> "{\"choices\":[{\"message\":{\"content\":"
        + "[{\"type\":\"text\",\"text\":\"부분1 \"},{\"type\":\"text\",\"text\":\"부분2\"}]},\"finish_reason\":\"stop\"}]}",
        capturedBody, capturedAuth);

    NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isTrue();
    assertThat(result.text()).isEqualTo("부분1 부분2");
  }

  @Test
  @DisplayName("given HTTP 429·5xx when complete then fallback 대상 기술적 실패로 판정한다")
  void complete_rateLimitAndServerErrorAreFallbackEligible() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();
    for (int status : new int[] {429, 502, 503}) {
      String baseUrl = startFakeNim(status, () -> "{\"error\":{\"message\":\"upstream busy\"}}",
          capturedBody, capturedAuth);

      NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

      assertThat(result.ok()).isFalse();
      assertThat(result.fallbackEligible()).isTrue();
      fakeNim.stop(0);
      fakeNim = null;
    }
  }

  @Test
  @DisplayName("given HTTP 401 when complete then 기술적 실패가 아니므로 fallback 없이 통제된 실패로 알린다")
  void complete_authenticationFailureIsNotEligible() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();
    String baseUrl = startFakeNim(401, () -> "{\"error\":{\"message\":\"Invalid API key\"}}",
        capturedBody, capturedAuth);

    NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isFalse();
    assertThat(result.fallbackEligible()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Invalid API key");
  }

  @Test
  @DisplayName("given 빈 content·interrupted·해석 불가 응답 when complete then 모두 기술적 실패로 fallback 대상")
  void complete_emptyInterruptedAndUnparsableAreEligible() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();
    String[] bodies = {
        "{\"choices\":[{\"message\":{\"content\":\"   \"},\"finish_reason\":\"stop\"}]}",
        "{\"choices\":[{\"message\":{\"content\":\"도중에 끊김\"},\"finish_reason\":\"interrupted\"}]}",
        "{\"choices\":[]}",
        "not-json-at-all",
    };
    for (String body : bodies) {
      String baseUrl = startFakeNim(200, () -> body, capturedBody, capturedAuth);

      NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

      assertThat(result.ok()).isFalse();
      assertThat(result.fallbackEligible()).isTrue();
      fakeNim.stop(0);
      fakeNim = null;
    }
  }

  @Test
  @DisplayName("given NIM에 연결할 수 없음 when complete then 예외 없이 연결 실패로 fallback 대상")
  void complete_connectionFailureIsEligible() {
    NewStartLlmCompletion result = transport("http://127.0.0.1:1", KEY).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isFalse();
    assertThat(result.fallbackEligible()).isTrue();
    assertThat(result.errorMessage()).isNotBlank();
  }

  @Test
  @DisplayName("given NVIDIA_API_KEY 미설정 when complete then 네트워크를 타지 않고 fallback 대상으로 돌려준다")
  void complete_withoutKeyDoesNotCallNetwork() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedAuth = new AtomicReference<>();
    String baseUrl = startFakeNim(200, () -> {
      hits.incrementAndGet();
      return "{\"choices\":[{\"message\":{\"content\":\"x\"},\"finish_reason\":\"stop\"}]}";
    }, capturedBody, capturedAuth);

    NewStartLlmCompletion result = transport(baseUrl, "").complete("시스템", "질문", 4000);

    assertThat(result.ok()).isFalse();
    assertThat(result.fallbackEligible()).isTrue();
    assertThat(hits.get()).isZero();
  }
}
