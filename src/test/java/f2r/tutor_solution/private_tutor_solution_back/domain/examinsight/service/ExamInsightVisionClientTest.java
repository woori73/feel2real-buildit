package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartVisionProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사진 인식 클라이언트 — 요청 형식·재시도·실패·비용 통제")
class ExamInsightVisionClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private HttpServer server;
  private NewStartVisionProperties props;
  private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
  private final List<String> auths = Collections.synchronizedList(new ArrayList<>());
  private final AtomicInteger calls = new AtomicInteger();

  /** 호출 번호(1부터)에 맞는 응답 [상태코드, 본문]을 돌려주는 가짜 서버. */
  private interface Script { Object[] respond(int call); }

  private void start(Script script) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", ex -> {
      bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      auths.add(ex.getRequestHeaders().getFirst("Authorization"));
      Object[] r = script.respond(calls.incrementAndGet());
      byte[] out = ((String) r[1]).getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", "application/json");
      ex.sendResponseHeaders((Integer) r[0], out.length);
      ex.getResponseBody().write(out);
      ex.close();
    });
    server.start();
    props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
  }

  private ExamInsightVisionClient client(int concurrency) {
    return new ExamInsightVisionClient(props, HttpClient.newHttpClient(), concurrency, 1L);
  }

  private static String ok(String content) {
    try {
      return MAPPER.writeValueAsString(java.util.Map.of("model", "m1",
          "choices", List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeEach
  void setUp() {
    props = new NewStartVisionProperties();
    props.setApiKey("test-key-not-real");
    props.setModel("vision-test");
    props.setMaxTokens(1234);
    props.setTimeoutSeconds(5);
  }

  @AfterEach
  void tearDown() {
    if (server != null) server.stop(0);
  }

  @Test
  @DisplayName("사진은 data URL 이미지와 OCR 규칙 프롬프트로 전송되고, 모델·인증 헤더가 설정을 따른다")
  void request_carriesImageAndRules() throws Exception {
    // given
    start(c -> new Object[] {200, ok("1. Choose the best word.\n① a ② b")});

    // when
    var t = client(2).transcribe(new byte[] {1, 2, 3}, "p1.jpg");

    // then
    assertThat(t.text()).isEqualTo("1. Choose the best word.\n① a ② b");
    JsonNode body = MAPPER.readTree(bodies.get(0));
    assertThat(body.get("model").asText()).isEqualTo("vision-test");
    assertThat(body.get("max_tokens").asInt()).isEqualTo(1234);
    JsonNode user = body.get("messages").get(1).get("content");
    assertThat(user.get(1).get("type").asText()).isEqualTo("image_url");
    assertThat(user.get(1).get("image_url").get("url").asText()).startsWith("data:image/jpeg;base64,");
    String system = body.get("messages").get(0).get("content").asText();
    assertThat(system).contains("[확인필요").contains("[판독불가]").contains("[필기:").contains("추측").contains("개인정보");
    assertThat(auths.get(0)).isEqualTo("Bearer test-key-not-real");
  }

  @Test
  @DisplayName("불확실 표시 개수를 세어 돌려주고, 코드 펜스로 감싼 응답은 벗겨 낸다")
  void uncertainMarkers_areCounted_fencesStripped() throws Exception {
    // given
    start(c -> new Object[] {200, ok("```\n3. The [확인필요: wether] is nice.\n[판독불가]\n[필기: 정답 ②]\n```")});

    // when
    var t = client(2).transcribe(new byte[] {1}, "p.jpg");

    // then
    assertThat(t.uncertainCount()).isEqualTo(2);
    assertThat(t.text()).doesNotContain("```");
    assertThat(t.text()).contains("[필기: 정답 ②]");
  }

  @Test
  @DisplayName("429 뒤 한 번만 재시도해 성공하면 결과를 돌려준다")
  void retriesOnceOn429() throws Exception {
    // given
    start(c -> c == 1 ? new Object[] {429, "{}"} : new Object[] {200, ok("text")});

    // when
    var t = client(2).transcribe(new byte[] {1}, "p.jpg");

    // then
    assertThat(t.text()).isEqualTo("text");
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("계속 5xx면 두 번(원래 + 재시도 1회)만 부르고 실패한다 — 비용 통제")
  void persistent5xx_stopsAfterTwoCalls() throws Exception {
    // given
    start(c -> new Object[] {503, "{}"});

    // when / then
    assertThatThrownBy(() -> client(2).transcribe(new byte[] {1}, "p.jpg"))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("VISION_FAILED"));
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("401/403·400은 재시도 없이 한 번에 실패한다")
  void clientErrors_doNotRetry() throws Exception {
    // given
    start(c -> new Object[] {401, "{\"error\":\"bad key sk-secret-123\"}"});

    // when / then: 메시지에 모델 응답 원문(키 포함)이 새지 않는다
    assertThatThrownBy(() -> client(2).transcribe(new byte[] {1}, "p.jpg"))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> {
          assertThat(e.getCode()).isEqualTo("VISION_FAILED");
          assertThat(e.getMessage()).doesNotContain("sk-secret").doesNotContain("test-key-not-real");
        });
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("빈 응답·content null(reasoning 토큰 소진)·깨진 JSON은 성공으로 치지 않는다")
  void emptyOrMalformedResponses_fail() throws Exception {
    for (String body : new String[] {ok("   "), "{\"choices\":[{\"message\":{\"content\":null}}]}", "not json"}) {
      calls.set(0);
      if (server != null) server.stop(0);
      start(c -> new Object[] {200, body});
      assertThatThrownBy(() -> client(2).transcribe(new byte[] {1}, "p.jpg"))
          .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("VISION_FAILED"));
    }
  }

  @Test
  @DisplayName("API 키가 없으면 호출하지 않고 사진 분석 불가를 알린다")
  void noApiKey_isUnavailableAndNeverCalls() throws Exception {
    // given
    props.setApiKey("");
    start(c -> new Object[] {200, ok("x")});
    var c = client(2);

    // when / then
    assertThat(c.available()).isFalse();
    assertThatThrownBy(() -> c.transcribe(new byte[] {1}, "p.jpg"))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("VISION_UNAVAILABLE"));
    assertThat(calls.get()).isZero();
  }

  @Test
  @DisplayName("동시 호출 상한을 넘는 요청은 기다리지 않고 바로 거절한다")
  void concurrencyCap_rejectsExtraCallsImmediately() throws Exception {
    // given: 서버가 첫 호출을 붙잡고 있는 동안 두 번째가 들어온다
    CountDownLatch inFlight = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    start(c -> {
      inFlight.countDown();
      try {
        release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return new Object[] {200, ok("slow")};
    });
    var c = client(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();

    try {
      var first = pool.submit(() -> c.transcribe(new byte[] {1}, "a.jpg"));
      assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();

      // when / then
      assertThatThrownBy(() -> c.transcribe(new byte[] {2}, "b.jpg"))
          .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("VISION_BUSY"));
      release.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS).text()).isEqualTo("slow");
      // 끝나면 자리가 돌아와 다시 호출할 수 있다
      assertThat(c.transcribe(new byte[] {3}, "c.jpg").text()).isEqualTo("slow");
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }
}
