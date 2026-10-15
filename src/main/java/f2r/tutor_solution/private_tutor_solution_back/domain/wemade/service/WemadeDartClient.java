package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/**
 * DART Open API 클라이언트 (opendart.fss.or.kr).
 *
 * 보안 원칙:
 * - API 키는 환경변수 DART_API_KEY에서만 읽는다. 코드·로그·PR에 절대 노출하지 않는다.
 * - 키 없이 열리는 report/viewer.do 경로는 robots.txt Disallow이므로 자동수집에 사용하지 않는다.
 *   (수동 참조용 sourceUrl로만 저장)
 * - DART cert_key는 "합법·안정·대량 수집"을 위한 것으로, 키가 없거나 네트워크가 막히면
 *   조용히 비활성화되고 수집 파이프라인의 다른 source에 영향을 주지 않는다.
 */
@Slf4j
@Component
public class WemadeDartClient {

    private static final String API_BASE = "https://opendart.fss.or.kr/api";
    private static final String ENV_KEY = "DART_API_KEY";

    private final HttpClient httpClient;

    public WemadeDartClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 키가 설정되어 있고 비어 있지 않으면 true. 키 값은 절대 반환/로그하지 않는다. */
    public boolean isConfigured() {
        String key = System.getenv(ENV_KEY);
        return key != null && !key.isBlank();
    }

    /** DART API 키 존재 여부만 로그한다 (값은 절대 로그하지 않음). */
    public void logConfigurationStatus() {
        log.info("WEMADE DART client configured={}", isConfigured());
    }

    /** 공시 목록 조회. 실패 시 빈 리스트 (graceful). */
    public List<DartDisclosure> fetchDisclosures(String corpCode, String bgnDe, String endDe) {
        List<DartDisclosure> out = new ArrayList<>();
        if (!isConfigured()) {
            log.info("WEMADE DART disclosure fetch skipped: {} not set", ENV_KEY);
            return out;
        }
        try {
            String url = API_BASE + "/list.json?crtfc_key=" + enc(System.getenv(ENV_KEY))
                    + "&corp_code=" + enc(corpCode)
                    + "&bgn_de=" + enc(bgnDe) + "&end_de=" + enc(endDe)
                    + "&page_no=1&page_count=100";
            String body = get(url);
            // 최소 파싱: list 배열의 rcept_no / report_nm / rcept_dt 추출
            Matcher m = Pattern.compile(
                    "\\{[^{}]*\"rcept_no\"\\s*:\\s*\"(\\d+)\"[^{}]*\"report_nm\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"rcept_dt\"\\s*:\\s*\"(\\d+)\"",
                    Pattern.DOTALL).matcher(body);
            while (m.find()) {
                out.add(new DartDisclosure(m.group(1), m.group(2), m.group(3)));
            }
            String status = extractJson(body, "status");
            if (!"000".equals(status)) {
                log.warn("WEMADE DART list API status={} message={}", status, extractJson(body, "message"));
                return List.of();
            }
        } catch (Exception e) {
            log.warn("WEMADE DART disclosure fetch failed (isolated): {}", e.getClass().getSimpleName());
        }
        return out;
    }

    /** corpCode.xml(ZIP)에서 회사명으로 corp_code 조회. 실패 시 empty. */
    public Optional<String> resolveCorpCode(String corpName) {
        if (!isConfigured()) return Optional.empty();
        try {
            String url = API_BASE + "/corpCode.xml?crtfc_key=" + enc(System.getenv(ENV_KEY));
            byte[] zip = getBytes(url);
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                zis.getNextEntry();
                String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile(
                        "<corp_code>(\\d+)</corp_code>\\s*<corp_name>([^<]*" + Pattern.quote(corpName) + "[^<]*)</corp_name>")
                        .matcher(xml);
                if (m.find()) return Optional.of(m.group(1));
            }
        } catch (Exception e) {
            log.warn("WEMADE DART corpCode resolve failed (isolated): {}", e.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    /**
     * 공시 원문 조회 (document.xml). 대용량일 수 있어 길이 제한을 둔다.
     * 키 없이 열리는 viewer.do 경로는 robots.txt Disallow이므로 사용하지 않는다.
     */
    public Optional<String> fetchDocument(String rceptNo) {
        if (!isConfigured()) return Optional.empty();
        try {
            String url = API_BASE + "/document.xml?crtfc_key=" + enc(System.getenv(ENV_KEY))
                    + "&rcept_no=" + enc(rceptNo);
            String body = get(url);
            if (body.length() > 400_000) body = body.substring(0, 400_000);
            return Optional.of(body);
        } catch (Exception e) {
            log.warn("WEMADE DART document fetch failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 수동 참조용 원문 URL (자동수집에 사용하지 않음). */
    public static String viewerUrl(String rcpNo) {
        return "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + rcpNo;
    }

    // ---- low-level ----

    private String get(String url) throws Exception {
        // URL에 키가 포함되므로 요청 URL 자체를 로그하지 않는다.
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(20))
                .GET().build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return res.body();
    }

    private byte[] getBytes(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<byte[]> res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return res.body();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String extractJson(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    public record DartDisclosure(String rceptNo, String reportName, String rceptDate) {}
}
