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
 * 용도: /wemade 분석용 조회 전용 클라이언트. 주문/자동매매 API는 절대 연결하지 않는다.
 *
 * 보안 원칙:
 * - APP Key/Secret은 환경변수 KIS_APP_KEY / KIS_APP_SECRET에서만 읽는다.
 *   코드·로그·PR에 절대 노출하지 않는다.
 * - 키가 없으면 조용히 비활성화된다 (PENDING).
 *
 * 이용약관 주의 (KIS 시세정보 서비스 이용약관 제7조):
 * - 고객은 회사의 승인 없이 시세정보를 재가공하거나 제3자에게 제공·재판매할 수 없다.
 * - 따라서 KIS 실시간 원시 시세/호가/체결 데이터를 /wemade 외부 회원에게
 *   그대로 재배포하지 않는다 (PRIVATE/INTERNAL).
 * - 외부 공개용 가격은 별도의 재배포 가능한 데이터 소스를 사용한다.
 * - 이 클라이언트가 반환하는 원시 데이터는 서버 내부 분석용으로만 사용한다.
 *
 * 확인된 TR (실키 검증 완료):
 * - FHKST01010100 주식현재가 시세: /uapi/domestic-stock/v1/quotations/inquire-price
 * - FHKST01010300 주식현재가 체결: /uapi/domestic-stock/v1/quotations/inquire-ccnl
 * - FHKST01010200 주식현재가 호가/예상체결:
 *   /uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn
 *   (output1=호가 10단계, output2=예상체결)
 * - FHKST01010900 종목별 투자자: /uapi/domestic-stock/v1/quotations/inquire-investor
 * - FHKST01010600 종목별 회원사: /uapi/domestic-stock/v1/quotations/inquire-member
 * - FHKST03010200 분봉조회: /uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice
 */
@Slf4j
@Component
public class WemadeKisClient {

    private static final String BASE = "https://openapi.koreainvestment.com:9443";
    private static final String ENV_KEY = "KIS_APP_KEY";
    private static final String ENV_SECRET = "KIS_APP_SECRET";
    private static final String QUOT_BASE = "/uapi/domestic-stock/v1/quotations";

    /** 주식현재가 시세 조회 TR ID */
    private static final String TR_QUOTE = "FHKST01010100";
    /** 주식현재가 체결 TR ID */
    private static final String TR_TRADE = "FHKST01010300";
    /** 주식현재가 호가/예상체결 TR ID (공식 open-trading-api 기준 경로) */
    private static final String TR_ORDERBOOK = "FHKST01010200";
    /** 종목별 투자자 TR ID */
    private static final String TR_INVESTOR = "FHKST01010900";
    /** 종목별 회원사 TR ID */
    private static final String TR_MEMBER = "FHKST01010600";
    /** 분봉조회 TR ID */
    private static final String TR_MINUTE = "FHKST03010200";

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
            JsonNode o = getJson(QUOT_BASE + "/inquire-price",
                    Map.of("FID_COND_MRKT_DIV_CODE", "J", "FID_INPUT_ISCD", symbol), TR_QUOTE)
                    .map(r -> r.path("output")).orElse(null);
            if (o == null) return Optional.empty();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("symbol", symbol);
            m.put("price", parseLong(o.path("stck_prpr").asText()));
            m.put("change", parseLong(o.path("prdy_vrss").asText()));
            m.put("changeRate", parseDouble(o.path("prdy_ctrt").asText()));
            m.put("volume", parseLong(o.path("acml_vol").asText()));
            m.put("tradeValue", parseLong(o.path("acml_tr_pbmn").asText()));
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
     * 실시간 체결 내역 (최대 30건).
     * 내부 분석용 (PRIVATE). 외부 API로 원시 전량을 노출하지 않는다.
     */
    public Optional<List<Map<String, Object>>> realtimeTrades(String symbol) {
        if (!isConfigured()) return Optional.empty();
        try {
            String token = accessToken().orElse(null);
            if (token == null) return Optional.empty();
            Optional<JsonNode> root = getJson(QUOT_BASE + "/inquire-ccnl",
                    Map.of("FID_COND_MRKT_DIV_CODE", "J", "FID_INPUT_ISCD", symbol), TR_TRADE);
            if (root.isEmpty()) return Optional.empty();
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode n : root.get().path("output")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("time", n.path("stck_cntg_hour").asText(null));
                m.put("price", parseLong(n.path("stck_prpr").asText()));
                m.put("change", parseLong(n.path("prdy_vrss").asText()));
                m.put("volume", parseLong(n.path("cntg_vol").asText()));
                m.put("buySell", n.path("ccnl_dvsn").asText(null));
                out.add(m);
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (Exception e) {
            log.warn("WEMADE KIS trades failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 실시간 호가 10단계 + 예상체결.
     * 공식 경로: /uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn
     * 내부 분석용 (PRIVATE). 외부 API로 원시 호가를 노출하지 않는다.
     */
    public Optional<Map<String, Object>> orderbook(String symbol) {
        if (!isConfigured()) return Optional.empty();
        try {
            String token = accessToken().orElse(null);
            if (token == null) return Optional.empty();
            Optional<JsonNode> root = getJson(QUOT_BASE + "/inquire-asking-price-exp-ccn",
                    Map.of("FID_COND_MRKT_DIV_CODE", "J", "FID_INPUT_ISCD", symbol), TR_ORDERBOOK);
            if (root.isEmpty()) return Optional.empty();
            JsonNode o1 = root.get().path("output1");
            JsonNode o2 = root.get().path("output2");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("symbol", symbol);
            List<Map<String, Object>> asks = new ArrayList<>();
            List<Map<String, Object>> bids = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("price", parseLong(o1.path("askp" + i).asText()));
                a.put("residue", parseLong(o1.path("askp_rsqn" + i).asText()));
                asks.add(a);
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("price", parseLong(o1.path("bidp" + i).asText()));
                b.put("residue", parseLong(o1.path("bidp_rsqn" + i).asText()));
                bids.add(b);
            }
            m.put("asks", asks);
            m.put("bids", bids);
            m.put("totalAskResidue", parseLong(o1.path("total_askp_rsqn").asText()));
            m.put("totalBidResidue", parseLong(o1.path("total_bidp_rsqn").asText()));
            // 예상체결 (장전/장후 시간대)
            m.put("expectedPrice", parseLong(o2.path("stck_prpr").asText()));
            m.put("expectedVolume", parseLong(o2.path("cntg_vol").asText()));
            return Optional.of(m);
        } catch (Exception e) {
            log.warn("WEMADE KIS orderbook failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 호가잔량 불균형 지표 계산 (내부 분석용).
     * @return (매수잔량합 - 매도잔량합) / (매수잔량합 + 매도잔량합), -1(매도 우위) ~ +1(매수 우위)
     */
    public Optional<Double> quoteImbalance(String symbol) {
        return orderbook(symbol).map(ob -> {
            long ask = ((Number) ob.getOrDefault("totalAskResidue", 0L)).longValue();
            long bid = ((Number) ob.getOrDefault("totalBidResidue", 0L)).longValue();
            long total = ask + bid;
            if (total == 0) return 0.0;
            return (bid - ask) / (double) total;
        });
    }

    /**
     * 종목별 투자자 (외국인/기관/개인) 일별 수급. 최대 30거래일.
     * KIS 응답 단위: 수량(주), 금액(백만원).
     */
    public Optional<List<Map<String, Object>>> investorFlows(String symbol) {
        if (!isConfigured()) return Optional.empty();
        try {
            String token = accessToken().orElse(null);
            if (token == null) return Optional.empty();
            Optional<JsonNode> root = getJson(QUOT_BASE + "/inquire-investor",
                    Map.of("FID_COND_MRKT_DIV_CODE", "J", "FID_INPUT_ISCD", symbol), TR_INVESTOR);
            if (root.isEmpty()) return Optional.empty();
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode n : root.get().path("output")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", n.path("stck_bsop_date").asText(null));
                m.put("close", parseLong(n.path("stck_clpr").asText()));
                m.put("foreignNetQty", parseLong(n.path("frgn_ntby_qty").asText()));
                m.put("foreignNetAmt", parseLong(n.path("frgn_ntby_tr_pbmn").asText()));
                m.put("institutionNetQty", parseLong(n.path("orgn_ntby_qty").asText()));
                m.put("institutionNetAmt", parseLong(n.path("orgn_ntby_tr_pbmn").asText()));
                m.put("retailNetQty", parseLong(n.path("prsn_ntby_qty").asText()));
                m.put("retailNetAmt", parseLong(n.path("prsn_ntby_tr_pbmn").asText()));
                out.add(m);
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (Exception e) {
            log.warn("WEMADE KIS investor flows failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 종목별 회원사(거래원) 매도/매수 상위.
     * TR FHKST01010600, /uapi/domestic-stock/v1/quotations/inquire-member (실키 검증됨).
     * 장 시간 외에는 0건일 수 있다. 제공되지 않는 데이터는 추정하지 않는다.
     */
    public Optional<Map<String, List<Map<String, Object>>>> memberRanking(String symbol) {
        if (!isConfigured()) return Optional.empty();
        try {
            String token = accessToken().orElse(null);
            if (token == null) return Optional.empty();
            Optional<JsonNode> root = getJson(QUOT_BASE + "/inquire-member",
                    Map.of("FID_COND_MRKT_DIV_CODE", "J", "FID_INPUT_ISCD", symbol), TR_MEMBER);
            if (root.isEmpty()) return Optional.empty();
            Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
            m.put("sell", parseMembers(root.get().path("output1"), "seln"));
            m.put("buy", parseMembers(root.get().path("output2"), "shnu"));
            if (m.get("sell").isEmpty() && m.get("buy").isEmpty()) return Optional.empty();
            return Optional.of(m);
        } catch (Exception e) {
            log.warn("WEMADE KIS member ranking failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private List<Map<String, Object>> parseMembers(JsonNode arr, String prefix) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : arr) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("broker", n.path("mbcr_name").asText(null));
            m.put("volume", parseLong(n.path(prefix + "_vol").asText()));
            m.put("amount", parseLong(n.path(prefix + "_tr_pbmn").asText()));
            out.add(m);
        }
        return out;
    }

    /**
     * 분봉 조회 (1분봉 기준, 최대 30봉).
     * FID_INPUT_HOUR_1 비우면 당일 분봉. 내부 분석용.
     */
    public Optional<List<Map<String, Object>>> minuteBars(String symbol) {
        if (!isConfigured()) return Optional.empty();
        try {
            String token = accessToken().orElse(null);
            if (token == null) return Optional.empty();
            Map<String, String> params = new LinkedHashMap<>();
            params.put("FID_COND_MRKT_DIV_CODE", "J");
            params.put("FID_INPUT_ISCD", symbol);
            params.put("FID_ETC_CLS_CODE", "");
            params.put("FID_INPUT_HOUR_1", "");
            params.put("FID_PW_DATA_INCU_YN", "Y");
            Optional<JsonNode> root = getJson(QUOT_BASE + "/inquire-time-itemchartprice", params, TR_MINUTE);
            if (root.isEmpty()) return Optional.empty();
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode n : root.get().path("output2")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("time", n.path("stck_cntg_hour").asText(null));
                m.put("price", parseLong(n.path("stck_prpr").asText()));
                m.put("open", parseLong(n.path("stck_oprc").asText()));
                m.put("high", parseLong(n.path("stck_hgpr").asText()));
                m.put("low", parseLong(n.path("stck_lwpr").asText()));
                m.put("volume", parseLong(n.path("cntg_vol").asText()));
                out.add(m);
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (Exception e) {
            log.warn("WEMADE KIS minute bars failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 당일 분봉 전체 (1일 차트용). FHKST03010200을 FID_INPUT_HOUR_1 커서로
     * 페이지네이션해서 장 시작(09:00)까지 거슬러 올라간다. 최대 14회 호출.
     * 키가 없거나 실패하면 empty — 호출자는 일봉으로 폴백한다.
     * 반환: 시간 오름차순, time 중복 제거.
     */
    public Optional<List<Map<String, Object>>> dayMinuteBars(String symbol) {
        if (!isConfigured()) return Optional.empty();
        try {
            String token = accessToken().orElse(null);
            if (token == null) return Optional.empty();
            List<Map<String, Object>> all = new ArrayList<>();
            String cursor = "";
            for (int page = 0; page < 14; page++) {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("FID_COND_MRKT_DIV_CODE", "J");
                params.put("FID_INPUT_ISCD", symbol);
                params.put("FID_ETC_CLS_CODE", "");
                params.put("FID_INPUT_HOUR_1", cursor);
                params.put("FID_PW_DATA_INCU_YN", "Y");
                Optional<JsonNode> root = getJson(QUOT_BASE + "/inquire-time-itemchartprice", params, TR_MINUTE);
                if (root.isEmpty()) break;
                JsonNode out2 = root.get().path("output2");
                if (!out2.isArray() || out2.size() == 0) break;
                List<Map<String, Object>> batch = new ArrayList<>();
                for (JsonNode n : out2) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", n.path("stck_cntg_hour").asText(null));
                    m.put("price", parseLong(n.path("stck_prpr").asText()));
                    m.put("open", parseLong(n.path("stck_oprc").asText()));
                    m.put("high", parseLong(n.path("stck_hgpr").asText()));
                    m.put("low", parseLong(n.path("stck_lwpr").asText()));
                    m.put("volume", parseLong(n.path("cntg_vol").asText()));
                    batch.add(m);
                }
                String earliest = null;
                for (Map<String, Object> b : batch) {
                    String t = (String) b.get("time");
                    if (t != null && (earliest == null || t.compareTo(earliest) < 0)) earliest = t;
                }
                all.addAll(batch);
                // 진전 없음 → 중단 (무한 루프 방지)
                if (earliest == null || (!cursor.isEmpty() && earliest.compareTo(cursor) >= 0)) break;
                // 장 시작(09:00)까지 확보했거나 충분히 모았으면 중단
                if (earliest.length() >= 6 && earliest.compareTo("090000") <= 0) break;
                if (all.size() >= 400) break;
                cursor = earliest;
            }
            // time 기준 중복 제거 + 오름차순 정렬
            Map<String, Map<String, Object>> dedup = new LinkedHashMap<>();
            for (Map<String, Object> b : all) {
                String t = (String) b.get("time");
                if (t != null) dedup.putIfAbsent(t, b);
            }
            List<Map<String, Object>> sorted = new ArrayList<>(dedup.values());
            sorted.sort((a, b) -> ((String) a.get("time")).compareTo((String) b.get("time")));
            return sorted.isEmpty() ? Optional.empty() : Optional.of(sorted);
        } catch (Exception e) {
            log.warn("WEMADE KIS day minute bars failed (isolated): {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    // ---- 내부 HTTP ----

    private Optional<JsonNode> getJson(String path, Map<String, String> params, String trId) {
        try {
            StringBuilder url = new StringBuilder(BASE + path + "?");
            for (Map.Entry<String, String> e : params.entrySet()) {
                url.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                   .append("=")
                   .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                   .append("&");
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("authorization", "Bearer " + accessToken().orElse(""))
                    .header("appkey", System.getenv(ENV_KEY))
                    .header("appsecret", System.getenv(ENV_SECRET))
                    .header("tr_id", trId)
                    .header("custtype", "P")
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> res = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                log.warn("WEMADE KIS {} HTTP {}", trId, res.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(res.body());
            if (!"0".equals(root.path("rt_cd").asText())) {
                log.warn("WEMADE KIS {} rt_cd={} msg={}", trId,
                        root.path("rt_cd").asText(), root.path("msg1").asText());
                return Optional.empty();
            }
            return Optional.of(root);
        } catch (Exception e) {
            log.warn("WEMADE KIS {} failed (isolated): {}", trId, e.getClass().getSimpleName());
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

    /**
     * WebSocket용 승인키 발급. POST /oauth2/Approval.
     * 키 값은 절대 로그/반환하지 않는다.
     */
    public Optional<String> websocketApprovalKey() {
        if (!isConfigured()) return Optional.empty();
        try {
            String body = "{\"grant_type\":\"client_credentials\","
                    + "\"appkey\":\"" + System.getenv(ENV_KEY) + "\","
                    + "\"secretkey\":\"" + System.getenv(ENV_SECRET) + "\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/oauth2/Approval"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(res.body());
            String key = root.path("approval_key").asText(null);
            if (key == null) {
                log.warn("WEMADE KIS approval key issue failed");
                return Optional.empty();
            }
            return Optional.of(key);
        } catch (Exception e) {
            log.warn("WEMADE KIS approval key failed (isolated): {}", e.getClass().getSimpleName());
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
