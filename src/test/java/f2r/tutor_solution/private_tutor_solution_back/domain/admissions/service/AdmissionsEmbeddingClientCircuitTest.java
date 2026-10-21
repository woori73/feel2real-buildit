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
}
