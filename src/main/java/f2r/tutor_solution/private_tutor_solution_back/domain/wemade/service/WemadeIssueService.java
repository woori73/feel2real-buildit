package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * /wemade 6개 쟁점 정적 원고 로더.
 * src/main/resources/wemade/issues.json을 앱 기동 시 1회 로딩.
 * 파싱 실패 시 기동 실패가 아니라 로그 + 빈 목록으로 degrade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WemadeIssueService {

    private final ObjectMapper objectMapper;

    @Getter
    private volatile List<Map<String, Object>> issues = Collections.emptyList();

    @Getter
    private volatile String version = "unknown";

    @PostConstruct
    public void load() {
        try {
            ClassPathResource res = new ClassPathResource("wemade/issues.json");
            try (InputStream in = res.getInputStream()) {
                Map<String, Object> root = objectMapper.readValue(in,
                        new TypeReference<Map<String, Object>>() {});
                Object v = root.get("version");
                if (v != null) version = String.valueOf(v);
                Object list = root.get("issues");
                if (list instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> parsed = (List<Map<String, Object>>) list;
                    issues = Collections.unmodifiableList(parsed);
                }
                log.info("WemadeIssueService loaded {} issues (version={})", issues.size(), version);
            }
        } catch (Exception e) {
            log.error("WemadeIssueService failed to load issues.json, degrading to empty list", e);
            issues = Collections.emptyList();
        }
    }

    public Optional<Map<String, Object>> findByNumber(int number) {
        return issues.stream()
                .filter(m -> number == toInt(m.get("number")))
                .findFirst();
    }

    /** viewer별 필터링. GUEST는 ①② 전체, ③~⑥은 질문 제목만. 절대 예외를 던지지 않음. */
    public List<Map<String, Object>> forViewer(String viewer) {
        try {
            if ("MEMBER".equals(viewer)) return issues;
            return issues.stream().map(m -> {
                int n = toInt(m.get("number"));
                if (n <= 2) return m;
                Map<String, Object> slim = new java.util.LinkedHashMap<>();
                slim.put("number", m.get("number"));
                slim.put("id", m.get("id"));
                slim.put("question", m.get("question"));
                slim.put("shortName", m.get("shortName"));
                return slim;
            }).toList();
        } catch (Exception e) {
            log.warn("WemadeIssueService.forViewer failed", e);
            return Collections.emptyList();
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return -1; }
    }
}
