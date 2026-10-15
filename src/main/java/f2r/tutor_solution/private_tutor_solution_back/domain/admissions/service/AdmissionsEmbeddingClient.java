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
                return null;
            }
            var root = objectMapper.readTree(resp.body());
            var embeddings = root.get("embeddings");
            if (embeddings == null || !embeddings.isArray() || embeddings.isEmpty()) {
                return null;
            }
            var first = embeddings.get(0);
            float[] vec = new float[first.size()];
            for (int i = 0; i < first.size(); i++) {
                vec[i] = (float) first.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            log.warn("admissions embedding error: {}", e.getMessage());
            return null;
        }
    }
}
