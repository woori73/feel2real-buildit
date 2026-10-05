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
 * MetaMuseSparkLlmTransport — NewStart 지피지기 Meta Muse Spark DIRECT primary.
 * OpenAI 호환 chat completions 계약(model·messages·temperature·max_tokens)으로 호출하고,
 * reasoning 모델 특성상 content가 null로 반환되면 기술적 실패로 보고한다.
 */
@DisplayName("MetaMuseSparkLlmTransport — Muse Spark DIRECT primary 계약")
class MetaMuseSparkLlmTransportTest {

  private static final String KEY = "meta-test-key-never-printed";

  private HttpServer fakeMeta;

  @AfterEach
  void stopFakeMeta() {
    if (fakeMeta != null) fakeMeta.stop(0);
  }

  private MetaMuseSparkLlmTransport transport(String baseUrl, String apiKey) {
    NewStartLlmProperties properties = new NewStartLlmProperties();
    properties.getMeta().setBaseUrl(baseUrl);
    properties.getMeta().setApiKey(apiKey);
    return new MetaMuseSparkLlmTransport(properties);
  }

  private String startFakeMeta(int status, String responseBody, AtomicInteger hits,
      AtomicReference<String> capturedBody) throws IOException {
    fakeMeta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeMeta.createContext("/chat/completions", exchange -> {
      hits.incrementAndGet();
      capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, response.length);
      try (var os = exchange.getResponseBody()) {
        os.write(response);
      }
    });
    fakeMeta.start();
    return "http://127.0.0.1:" + fakeMeta.getAddress().getPort();
  }

  @Test
  @DisplayName("given 정상 응답 when complete then Muse Spark 모델로 호출하고 content만 반환한다")
  void complete_callsMuseSparkAndReturnsContent() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    AtomicReference<String> capturedBody = new AtomicReference<>();
    String baseUrl = startFakeMeta(200, "{\"model\":\"muse-spark-1.3-contributor\","
        + "\"choices\":[{\"finish_reason\":\"stop\","
        + "\"message\":{\"content\":\"새출발기금 답변\",\"reasoning_content\":\"노출 금지\"}}]}",
        hits, capturedBody);

    NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isTrue();
    assertThat(result.text()).isEqualTo("새출발기금 답변");
    assertThat(result.text()).doesNotContain("노출 금지");
    assertThat(result.model()).isEqualTo("muse-spark-1.3-contributor");
    assertThat(result.provider()).isEqualTo("meta-muse-spark");
    assertThat(hits.get()).isEqualTo(1);
    assertThat(capturedBody.get())
        .contains("\"model\":\"muse-spark-1.3-contributor\"")
        .contains("\"temperature\":0.25")
        .contains("\"max_tokens\":8000")
        .contains("\"role\":\"system\"")
        .contains("\"content\":\"시스템\"")
        .contains("\"content\":\"질문\"");
    assertThat(capturedBody.get()).doesNotContain(KEY);
  }

  @Test
  @DisplayName("given reasoning 토큰 소진으로 content가 null when complete then 기술적 실패(fallbackEligible=true)로 보고한다")
  void complete_nullContentIsFallbackEligible() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    AtomicReference<String> capturedBody = new AtomicReference<>();
    String baseUrl = startFakeMeta(200, "{\"model\":\"muse-spark-1.3-contributor\","
        + "\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null}}]}",
        hits, capturedBody);

    NewStartLlmCompletion result = transport(baseUrl, KEY).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isFalse();
    assertThat(result.fallbackEligible()).isTrue();
  }

  @Test
  @DisplayName("given 429·5xx when complete then fallbackEligible=true, 그 외 4xx는 false")
  void complete_retryableStatusesAreEligible() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    AtomicReference<String> capturedBody = new AtomicReference<>();

    String retryable = startFakeMeta(429, "{\"error\":\"rate limited\"}", hits, capturedBody);
    assertThat(transport(retryable, KEY).complete("시스템", "질문", 4000).fallbackEligible()).isTrue();
    fakeMeta.stop(0);
    fakeMeta = null;

    String serverError = startFakeMeta(500, "{\"error\":\"oops\"}", hits, capturedBody);
    assertThat(transport(serverError, KEY).complete("시스템", "질문", 4000).fallbackEligible()).isTrue();
    fakeMeta.stop(0);
    fakeMeta = null;

    String authError = startFakeMeta(401, "{\"error\":\"unauthorized\"}", hits, capturedBody);
    NewStartLlmCompletion auth = transport(authError, KEY).complete("시스템", "질문", 4000);
    assertThat(auth.ok()).isFalse();
    assertThat(auth.fallbackEligible()).isFalse();
  }

  @Test
  @DisplayName("given META_JIPJIGI_API_KEY 미설정 when complete then 네트워크를 타지 않고 실패한다")
  void complete_withoutKeyDoesNotCallNetwork() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    AtomicReference<String> capturedBody = new AtomicReference<>();
    String baseUrl = startFakeMeta(200, "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}",
        hits, capturedBody);

    NewStartLlmCompletion result = transport(baseUrl, "").complete("시스템", "질문", 4000);

    assertThat(result.ok()).isFalse();
    assertThat(result.fallbackEligible()).isFalse();
    assertThat(hits.get()).isZero();
  }
}
