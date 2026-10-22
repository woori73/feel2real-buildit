package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config.AdmissionsEmbeddingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 입시상담 임베딩 서비스 HTTP 클라이언트.
 * 지피지기의 EmbeddingClient 패턴을 입시 전용 설정으로 적용.
 * 실패 시 null 반환 → 호출자는 LIKE 검색으로 폴백한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionsEmbeddingClient {

    private final AdmissionsEmbeddingProperties props;
    private final ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    // 서킷브레이커: 연속 실패가 임계값에 이르면 일정 시간 호출을 건너뛴다(장애 중 질문 지연 방지).
    private final java.util.concurrent.atomic.AtomicInteger consecutiveFailures = new java.util.concurrent.atomic.AtomicInteger();
    private volatile long openUntilMillis = 0L;
    private java.util.function.LongSupplier clock = System::currentTimeMillis;
    /** 실제로 서버에 요청을 보낸 횟수(테스트·운영 점검용). */
    final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
    /** 문서(재색인) 요청 횟수. 서킷과 무관. */
    final java.util.concurrent.atomic.AtomicInteger docAttempts = new java.util.concurrent.atomic.AtomicInteger();

    void setClockForTest(java.util.function.LongSupplier clock) { this.clock = clock; }

    boolean isCircuitOpen() { return clock.getAsLong() < openUntilMillis; }

    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= props.getCircuitFailureThreshold()) {
            openUntilMillis = clock.getAsLong() + props.getCircuitOpenMs();
            consecutiveFailures.set(0);
            log.warn("admissions embedding circuit opened for {}ms", props.getCircuitOpenMs());
        }
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
    }

    private HttpClient client() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(props.getTimeoutMs()))
                            .build();
                }
            }
        }
        return httpClient;
    }

    /** 문서 임베딩 결과. vector가 null이면 실패이며 retryable이 재시도 가치 여부를 알려준다. */
    public record DocResult(float[] vector, String failure, boolean retryable) {
        static DocResult ok(float[] v) { return new DocResult(v, null, false); }
        static DocResult fail(String reason, boolean retryable) { return new DocResult(null, reason, retryable); }
    }

    /**
     * 질문(query) 임베딩. 짧은 timeoutMs + 서킷브레이커를 쓴다. 실패하면 null (키워드 검색 폴백 신호).
     */
    public float[] embed(String text) {
        String url = props.getServiceUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        if (isCircuitOpen()) {
            return null;
        }
        attempts.incrementAndGet();
        DocResult r = call(url, text, "query", props.getTimeoutMs());
        if (r.vector() == null) {
            recordFailure();
            return null;
        }
        recordSuccess();
        return r.vector();
    }

    /**
     * 문서(document) 임베딩 — 재색인 전용. 긴 indexTimeoutMs를 쓰고 질문용 서킷브레이커는 건드리지 않는다
     * (재색인 실패가 사용자 질문 검색을 막으면 안 되고, 반대도 마찬가지).
     */
    public DocResult embedDocument(String text) {
        String url = props.getServiceUrl();
        if (url == null || url.isBlank()) {
            return DocResult.fail("임베딩 서비스 URL 미설정", false);
        }
        docAttempts.incrementAndGet();
        return call(url, text, "document", props.getIndexTimeoutMs());
    }

    private DocResult call(String url, String text, String task, int timeoutMs) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("texts", List.of(text));
            body.put("task", task);
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/embed"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("admissions embedding failed: task={} status={}", task, resp.statusCode());
                // 503=서버가 다른 요청 처리 중(동시성 1) → 재시도 가치 있음. 그 외 5xx도 일시 장애로 본다.
                return DocResult.fail("임베딩 서버 응답 status=" + resp.statusCode(),
                        resp.statusCode() >= 500 || resp.statusCode() == 429);
            }
            var embeddings = objectMapper.readTree(resp.body()).get("embeddings");
            if (embeddings == null || !embeddings.isArray() || embeddings.isEmpty()) {
                return DocResult.fail("임베딩 응답에 embeddings 없음", false);
            }
            var first = embeddings.get(0);
            float[] vec = new float[first.size()];
            for (int i = 0; i < first.size(); i++) {
                vec[i] = (float) first.get(i).asDouble();
            }
            return DocResult.ok(vec);
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("admissions embedding timeout: task={} after {}ms", task, timeoutMs);
            return DocResult.fail("타임아웃 " + timeoutMs + "ms", true);
        } catch (java.io.IOException e) {
            log.warn("admissions embedding io error: task={} {}", task, e.getMessage());
            return DocResult.fail("연결 오류: " + e.getMessage(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DocResult.fail("중단됨", false);
        } catch (Exception e) {
            log.warn("admissions embedding error: task={} {}", task, e.getMessage());
            return DocResult.fail("오류: " + e.getMessage(), false);
        }
    }
}
