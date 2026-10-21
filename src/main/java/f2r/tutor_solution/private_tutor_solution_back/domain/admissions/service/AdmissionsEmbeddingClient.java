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

    /**
     * 텍스트를 임베딩한다. 실패하면 null (폴백 신호).
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
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("texts", List.of(text));
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/embed"))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("admissions embedding failed: status={}", resp.statusCode());
                recordFailure();
                return null;
            }
            var root = objectMapper.readTree(resp.body());
            var embeddings = root.get("embeddings");
            if (embeddings == null || !embeddings.isArray() || embeddings.isEmpty()) {
                recordFailure();
                return null;
            }
            var first = embeddings.get(0);
            float[] vec = new float[first.size()];
            for (int i = 0; i < first.size(); i++) {
                vec[i] = (float) first.get(i).asDouble();
            }
            recordSuccess();
            return vec;
        } catch (Exception e) {
            log.warn("admissions embedding error: {}", e.getMessage());
            recordFailure();
            return null;
        }
    }
}
