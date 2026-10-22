package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("임베딩 클라이언트 — 타임아웃·서킷브레이커")
class AdmissionsEmbeddingClientCircuitTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private AdmissionsEmbeddingProperties props(String url, int timeoutMs) {
        var p = new AdmissionsEmbeddingProperties();
        p.setServiceUrl(url);
        p.setTimeoutMs(timeoutMs);
        p.setCircuitFailureThreshold(2);
        p.setCircuitOpenMs(30_000);
        return p;
    }

    @Test
    @DisplayName("given 응답하지 않는 서버 when 호출하면 then 타임아웃(1.5초) 안에 null로 끝난다")
    void hangingServer_returnsNullWithinTimeout() throws Exception {
        // given: 연결은 받지만 응답하지 않는 서버
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embed", ex -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
            ex.close();
        });
        server.start();
        var client = new AdmissionsEmbeddingClient(props("http://127.0.0.1:" + server.getAddress().getPort(), 1500), new ObjectMapper());

        // when
        long t0 = System.nanoTime();
        float[] vec = client.embed("질문");
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        // then
        assertThat(vec).isNull();
        assertThat(elapsedMs).as("타임아웃 이내").isLessThan(3000);
    }

    @Test
    @DisplayName("given 연속 실패 when 임계값에 이르면 then 서킷이 열려 서버 호출 없이 즉시 null, 시간이 지나면 다시 시도한다")
    void circuitOpensAfterConsecutiveFailures_andRecovers() {
        // given: 닫힌 포트(즉시 연결 실패)와 가짜 시계
        var client = new AdmissionsEmbeddingClient(props("http://127.0.0.1:1", 500), new ObjectMapper());
        AtomicLong now = new AtomicLong(1_000_000L);
        client.setClockForTest(now::get);

        // when: 2회 실패 → 서킷 열림
        assertThat(client.embed("a")).isNull();
        assertThat(client.embed("b")).isNull();
        int attemptsAtOpen = client.attempts.get();

        // then: 열려 있는 동안은 호출하지 않는다
        assertThat(client.isCircuitOpen()).isTrue();
        long t0 = System.nanoTime();
        assertThat(client.embed("c")).isNull();
        assertThat((System.nanoTime() - t0) / 1_000_000).as("즉시 반환").isLessThan(50);
        assertThat(client.attempts.get()).isEqualTo(attemptsAtOpen);

        // when: 열림 시간이 지나면
        now.addAndGet(30_001);

        // then: 다시 시도한다(반개방)
        assertThat(client.isCircuitOpen()).isFalse();
        client.embed("d");
        assertThat(client.attempts.get()).isEqualTo(attemptsAtOpen + 1);
    }

    @Test
    @DisplayName("given 정상 응답 when 호출하면 then 벡터를 돌려주고 실패 카운트가 초기화된다")
    void success_resetsFailureCount() throws Exception {
        // given
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embed", ex -> {
            byte[] body = "{\"embeddings\":[[0.1,0.2,0.3]]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        var client = new AdmissionsEmbeddingClient(props("http://127.0.0.1:" + server.getAddress().getPort(), 1500), new ObjectMapper());

        // when
        float[] vec = client.embed("질문");

        // then
        assertThat(vec).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(client.isCircuitOpen()).isFalse();
    }

    @Test
    @DisplayName("given service-url 미설정 when 호출하면 then 서버를 부르지 않고 null")
    void blankUrl_returnsNull() {
        var client = new AdmissionsEmbeddingClient(props("", 1500), new ObjectMapper());
        assertThat(client.embed("질문")).isNull();
        assertThat(client.attempts.get()).isZero();
    }

    // ── 재색인(document) 모드 ─────────────────────────────────────

    private HttpServer startServer(java.util.function.Consumer<com.sun.net.httpserver.HttpExchange> handler) throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/embed", ex -> { handler.accept(ex); ex.close(); });
        srv.start();
        return srv;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code, String json) {
        try {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b);
        } catch (java.io.IOException ignored) { }
    }

    @Test
    @DisplayName("given 질문·문서 호출 when 요청하면 then 서버가 task=query / task=document를 각각 받는다")
    void queryAndDocumentModes_sendDistinctTask() throws Exception {
        // given
        java.util.List<String> bodies = new java.util.concurrent.CopyOnWriteArrayList<>();
        server = startServer(ex -> {
            try { bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); } catch (Exception ignored) { }
            respond(ex, 200, "{\"embeddings\":[[0.1,0.2]]}");
        });
        var client = new AdmissionsEmbeddingClient(props("http://127.0.0.1:" + server.getAddress().getPort(), 1500), new ObjectMapper());

        // when
        client.embed("질문");
        client.embedDocument("문서");

        // then
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0)).contains("\"task\":\"query\"");
        assertThat(bodies.get(1)).contains("\"task\":\"document\"");
    }

    @Test
    @DisplayName("given 질문 타임아웃(1.5s)보다 느린 서버 when 문서 임베딩하면 then 재색인 타임아웃 안에서는 성공한다")
    void documentTimeout_isSeparateFromQueryTimeout() throws Exception {
        // given: 2초 걸려 응답하는 서버, 질문 타임아웃 500ms / 재색인 타임아웃 5s
        server = startServer(ex -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
            respond(ex, 200, "{\"embeddings\":[[0.5]]}");
        });
        var p = props("http://127.0.0.1:" + server.getAddress().getPort(), 500);
        p.setIndexTimeoutMs(5000);
        var client = new AdmissionsEmbeddingClient(p, new ObjectMapper());

        // when
        var doc = client.embedDocument("긴 문서");
        float[] query = client.embed("질문");

        // then
        assertThat(doc.vector()).containsExactly(0.5f);
        assertThat(query).as("질문은 짧은 타임아웃에 걸린다").isNull();
    }

    @Test
    @DisplayName("given 문서 임베딩이 연속 실패 when 호출하면 then 질문용 서킷은 열리지 않는다(브레이커 분리)")
    void documentFailures_doNotOpenQueryCircuit() {
        // given: 닫힌 포트
        var client = new AdmissionsEmbeddingClient(props("http://127.0.0.1:1", 300), new ObjectMapper());

        // when: 임계값(2)보다 많이 실패
        for (int i = 0; i < 4; i++) {
            assertThat(client.embedDocument("문서").vector()).isNull();
        }

        // then
        assertThat(client.isCircuitOpen()).isFalse();
        assertThat(client.attempts.get()).as("질문 시도 수는 그대로").isZero();
        assertThat(client.docAttempts.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("given 질문 서킷이 열린 상태 when 문서 임베딩하면 then 서킷과 무관하게 서버를 호출한다")
    void openQueryCircuit_doesNotBlockDocument() throws Exception {
        // given: 질문 쪽에서 서킷을 연다
        var client = new AdmissionsEmbeddingClient(props("http://127.0.0.1:1", 300), new ObjectMapper());
        client.embed("a");
        client.embed("b");
        assertThat(client.isCircuitOpen()).isTrue();

        // when
        client.embedDocument("문서");

        // then
        assertThat(client.docAttempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("given 서버 응답 코드 when 문서 임베딩 실패하면 then 503/타임아웃/연결오류만 재시도 대상으로 분류한다")
    void failureClassification_retryable() throws Exception {
        // given: 503
        server = startServer(ex -> respond(ex, 503, "busy"));
        var client = new AdmissionsEmbeddingClient(props("http://127.0.0.1:" + server.getAddress().getPort(), 500), new ObjectMapper());

        // when / then: 503 → 재시도 대상
        var busy = client.embedDocument("x");
        assertThat(busy.vector()).isNull();
        assertThat(busy.retryable()).isTrue();
        assertThat(busy.failure()).contains("503");
        server.stop(0);

        // 400 → 재시도 대상 아님
        server = startServer(ex -> respond(ex, 400, "bad"));
        client = new AdmissionsEmbeddingClient(props("http://127.0.0.1:" + server.getAddress().getPort(), 500), new ObjectMapper());
        assertThat(client.embedDocument("x").retryable()).isFalse();

        // 연결 오류 → 재시도 대상
        var down = new AdmissionsEmbeddingClient(props("http://127.0.0.1:1", 300), new ObjectMapper());
        assertThat(down.embedDocument("x").retryable()).isTrue();

        // 타임아웃 → 재시도 대상
        server.stop(0);
        server = startServer(ex -> { try { Thread.sleep(1500); } catch (InterruptedException ignored) { } });
        var p = props("http://127.0.0.1:" + server.getAddress().getPort(), 300);
        p.setIndexTimeoutMs(300);
        var slow = new AdmissionsEmbeddingClient(p, new ObjectMapper()).embedDocument("x");
        assertThat(slow.retryable()).isTrue();
        assertThat(slow.failure()).contains("타임아웃");
    }

    @Test
    @DisplayName("given service-url 미설정 when 문서 임베딩하면 then 재시도 불가 실패")
    void documentBlankUrl_notRetryable() {
        var r = new AdmissionsEmbeddingClient(props("", 1500), new ObjectMapper()).embedDocument("x");
        assertThat(r.vector()).isNull();
        assertThat(r.retryable()).isFalse();
    }
}
