package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한국투자증권(KIS) Open API 클라이언트 (openapi.koreainvestment.com:9443).
 *
 * 용도: /wemade 첫 화면의 실시간 현재가 1건 조회 (보조 소스).
 *
 * 보안 원칙:
 * - APP Key/Secret은 환경변수 KIS_APP_KEY / KIS_APP_SECRET에서만 읽는다.
 *   코드·로그·PR에 절대 노출하지 않는다.
 * - 키가 없으면 조용히 비활성화되고 pykrx 지연 시세로 fallback한다 (PENDING).
 *
 * 이용약관 주의 (KIS 시세정보 서비스 이용약관 제7조):
 * - 고객은 회사의 승인 없이 시세정보를 재가공하거나 제3자에게 제공·재판매할 수 없다.
 * - 따라서 KIS 실시간 원시 시세를 /wemade 외부 회원에게 그대로 재배포하지 않는다.
 * - 약관상 공개 범위 확인 전까지 실시간 시세는 내부 분석용으로만 사용하고,
 *   외부 표시 범위를 제한한다 (현재가 1건 + "실시간" 배지 수준, 원시 호가/체결 전량 미제공).
 */
@Slf4j
@Component
public class WemadeKisClient {

    private static final String BASE = "https://openapi.koreainvestment.com:9443";
    private static final String ENV_KEY = "KIS_APP_KEY";
    private static final String ENV_SECRET = "KIS_APP_SECRET";
    /** 주식현재가 시세 조회 TR ID */
    private static final String TR_QUOTE = "FHKST01010100";
    /** 주식당일거래원 TR ID (KIS API 포털 문서 기준 — 실키로 검증 필요) */
    private static final String TR_BROKER = "FHPST01060000";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String cachedToken;
    private volatile long tokenExpiresAt;

    public WemadeKisClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 키가 모두 설정되어 있으면 true. 키 값은 절대 반환/로그하지 않는다. */
    public boolean isConfigured() {
        String k = System.getenv(ENV_KEY);
        String s = System.getenv(ENV_SECRET);
        return k != null && !k.isBlank() && s != null && !s.isBlank();
    }

    /** 실시간 현재가 1건 조회. 키가 없거나 실패하면 empty (호출자는 pykrx로 fallback). */
    public Optional<Map<String, Object>> realtimeQuote(String symbol) {
        if (!isConfigured()) {
            log.info("WEMADE KIS quote skipped: {} / {} not set (PENDING)", ENV_KEY, ENV_SECRET);
            return Optional.empty();
        }
        try {
            String token = accessToken().orElse(null);
            if (token == null) return Optional.empty();
            String url = BASE + "/uapi/domestic-stock/v1/quotations/inquire-price"
                    + "?fid_cond_mrkt_div_code=J"
                    + "&fid_input_iscd=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", System.getenv(ENV_KEY))
                    .header("appsecret", System.getenv(ENV_SECRET))
                    .header("tr_id", TR_QUOTE)
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> res = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                log.warn("WEMADE KIS quote HTTP {}", res.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(res.body());
            if (!"0".equals(root.path("rt_cd").asText())) {
                log.warn("WEMADE KIS quote rt_cd={} msg={}", root.path("rt_cd").asText(),
                        root.path("msg1").asText());
                return Optional.empty();
            }
            JsonNode o = root.path("output");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("symbol", symbol);
            m.put("price", parseLong(o.path("stck_prpr").asText()));
            m.put("change", parseLong(o.path("prdy_vrss").asText()));
            m.put("changeRate", parseDouble(o.path("prdy_ctrt").asText()));
            m.put("volume", parseLong(o.path("acml_vol").asText()));
            m.put("open", parseLong(o.path("stck_oprc").asText()));
            m.put("high", parseLong(o.path("stck_hgpr").asText()));
            m.put("low", parseLong(o.path("stck_lwpr").asText()));
            m.put("realtime", true);
            m.put("source", "KIS");
            return Optional.of(m);
        } catch (Exception e) {
            // 토큰/키 값은 절대 로그하지 않는다.
            log.warn("WEMADE KIS quote failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 당일 매도/매수 상위 거래원 (증권사 창구) 조회 시도.
     * TR ID는 KIS API 포털 문서 기준이며 실키로 검증이 필요하다.
     * 제공되지 않거나 실패하면 empty를 반환한다 — 제공되지 않는 데이터를 추정해서 만들지 않는다.
     */
    public Optional<List<Map<String, Object>>> brokerRanking(String symbol) {
        if (!isConfigured()) {
            log.info("WEMADE KIS broker ranking skipped: keys not set (PENDING)");
            return Optional.empty();
        }
        try {
            String token = accessToken().orElse(null);
            if (token == null) return Optional.empty();
            String url = BASE + "/uapi/domestic-stock/v1/quotations/inquire-daily-broker"
                    + "?fid_cond_mrkt_div_code=J"
                    + "&fid_input_iscd=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", System.getenv(ENV_KEY))
                    .header("appsecret", System.getenv(ENV_SECRET))
                    .header("tr_id", TR_BROKER)
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> res = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) return Optional.empty();
            JsonNode root = objectMapper.readTree(res.body());
            if (!"0".equals(root.path("rt_cd").asText())) {
                log.info("WEMADE KIS broker ranking unavailable: rt_cd={}",
                        root.path("rt_cd").asText());
                return Optional.empty();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode n : root.path("output")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("broker", n.path("mbcr_name").asText(null));
                m.put("sellQty", parseLong(n.path("seln_qry").asText()));
                m.put("buyQty", parseLong(n.path("shnu_qry").asText()));
                out.add(m);
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (Exception e) {
            log.warn("WEMADE KIS broker ranking failed (isolated): {}",
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    // ---- OAuth ----

    private Optional<String> accessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return Optional.of(cachedToken);
        }
        try {
            String body = "{\"grant_type\":\"client_credentials\","
                    + "\"appkey\":\"" + System.getenv(ENV_KEY) + "\","
                    + "\"appsecret\":\"" + System.getenv(ENV_SECRET) + "\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/oauth2/tokenP"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(res.body());
            String token = root.path("access_token").asText(null);
            if (token == null) {
                log.warn("WEMADE KIS token issue failed");
                return Optional.empty();
            }
            cachedToken = token;
            // KIS 토큰 유효기간은 보통 24시간. 여유를 두고 20시간으로 캐시한다.
            tokenExpiresAt = System.currentTimeMillis() + 20L * 3600 * 1000;
            return Optional.of(token);
        } catch (Exception e) {
            log.warn("WEMADE KIS token failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (Exception e) { return null; }
    }

    private static Double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return null; }
    }
}
