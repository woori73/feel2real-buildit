package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.cluster.MysqlNamedLocks;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KIS 실시간 WebSocket 클라이언트 (실전: ws://ops.koreainvestment.com:21000).
 *
 * 구독 TR (KIS 공식 open-trading-api 기준, koreainvestment/open-trading-api):
 * - 체결: H0STCNT0 (KRX) / H0NXCNT0 (NXT) / H0UNCNT0 (통합)
 * - 호가:   H0STASP0 (KRX) / H0NXASP0 (NXT) / H0UNASP0 (통합)
 * - 회원사: H0STMBC0 (KRX) / H0NXMBC0 (NXT) / H0UNMBC0 (통합)
 *
 * 시장 분리 원칙:
 * - 모든 틱에 exchange (KRX|NXT|UNIFIED) 태그. KRX와 NXT를 덮어쓰거나 출처 없이 합산하지 않는다.
 * - session은 틱의 MARKET_CLS_CODE 필드 우선 (1=프리, 2=정규, 3=애프터, 5=종가).
 *
 * 보안/운영 원칙:
 * - 원시 틱 데이터는 메모리에만 유지하고 DB에 저장하지 않는다 (PRIVATE).
 * - 이상수급 탐지 서비스가 스냅샷을 읽어 파생 지표만 DB에 기록한다 (INTERNAL).
 * - 키/승인키는 절대 로그하지 않는다.
 * - 장 시간 외에는 연결하지 않고 조용히 대기한다 (NXT: 평일 08:00~20:00).
 * - 연결 실패 시 지수 백오프로 재시도하되, 실패가 다른 기능을 막지 않는다.
 * - 주문 API는 연결하지 않는다 (조회/수집/분석 전용).
 */
@Slf4j
@Component
public class WemadeKisWebSocket {

    private static final String WS_URL = "ws://ops.koreainvestment.com:21000";
    private static final String SYMBOL = "112040";

    // TR ID (KIS 공식 문서 기준)
    private static final String TR_TRADE_KRX = "H0STCNT0";
    private static final String TR_TRADE_NXT = "H0NXCNT0";
    private static final String TR_TRADE_UNI = "H0UNCNT0";
    private static final String TR_QUOTE_KRX = "H0STASP0";
    private static final String TR_QUOTE_NXT = "H0NXASP0";
    private static final String TR_QUOTE_UNI = "H0UNASP0";
    private static final String TR_MEMBER_KRX = "H0STMBC0";
    private static final String TR_MEMBER_NXT = "H0NXMBC0";
    private static final String TR_MEMBER_UNI = "H0UNMBC0";

    /** TR ID → exchange 매핑 */
    private static final Map<String, String> TR_EXCHANGE = Map.of(
            TR_TRADE_KRX, "KRX", TR_QUOTE_KRX, "KRX", TR_MEMBER_KRX, "KRX",
            TR_TRADE_NXT, "NXT", TR_QUOTE_NXT, "NXT", TR_MEMBER_NXT, "NXT",
            TR_TRADE_UNI, "UNIFIED", TR_QUOTE_UNI, "UNIFIED", TR_MEMBER_UNI, "UNIFIED");

    /** TR ID → 데이터 종류 매핑 */
    private static final Map<String, String> TR_KIND = Map.of(
            TR_TRADE_KRX, "TRADE", TR_TRADE_NXT, "TRADE", TR_TRADE_UNI, "TRADE",
            TR_QUOTE_KRX, "QUOTE", TR_QUOTE_NXT, "QUOTE", TR_QUOTE_UNI, "QUOTE",
            TR_MEMBER_KRX, "MEMBER", TR_MEMBER_NXT, "MEMBER", TR_MEMBER_UNI, "MEMBER");

    private final WemadeKisClient kisClient;
    private final ObjectMapper objectMapper;
    private final MysqlNamedLocks locks;
    private final boolean websocketEnabled;
    private volatile MysqlNamedLocks.Session leadership;
    private volatile Thread loopThread;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "kis-leader-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private volatile WebSocket webSocket;

    /** 시장별 최신 호가 스냅샷 (내부 분석용) */
    private final Map<String, Map<String, Object>> latestQuoteByEx = new ConcurrentHashMap<>();
    /** 시장별 최신 체결 스냅샷 (내부 분석용) */
    private final Map<String, Map<String, Object>> latestTradeByEx = new ConcurrentHashMap<>();
    /** 시장별 최근 체결 버퍼 (대량체결 탐지용, 시장당 최대 200건, 메모리만) */
    private final Map<String, List<Map<String, Object>>> tradeBufferByEx = new ConcurrentHashMap<>();
    /** 시장별 최신 회원사 스냅샷 (내부 분석용) */
    private final Map<String, Map<String, Object>> latestMemberByEx = new ConcurrentHashMap<>();

    public WemadeKisWebSocket(WemadeKisClient kisClient, ObjectMapper objectMapper,
            MysqlNamedLocks locks, @Value("${wemade.kis.websocket-enabled:false}") boolean websocketEnabled) {
        this.kisClient = kisClient;
        this.objectMapper = objectMapper;
        this.locks = locks;
        this.websocketEnabled = websocketEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!websocketEnabled || !kisClient.isConfigured()) {
            log.info("WEMADE KIS websocket disabled: keys not set (PENDING)");
            return;
        }
        if (!enabled.compareAndSet(false, true)) return;
        running.set(true);
        watchdog.scheduleWithFixedDelay(this::checkLeadership, 1, 1, TimeUnit.SECONDS);
        Thread t = new Thread(this::runLoop, "wemade-kis-ws");
        t.setDaemon(true);
        loopThread = t;
        t.start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        enabled.set(false);
        if (loopThread != null) loopThread.interrupt();
        watchdog.shutdownNow();
        relinquishLeadership();
    }

    /** WebSocket 실시간 수신 여부 */
    public boolean isLive() {
        return enabled.get() && leadership != null && webSocket != null && !webSocket.isOutputClosed();
    }

    /** 시장별 최신 호가 스냅샷 (내부 분석용). 외부 API로 원시를 노출하지 않는다. */
    public Optional<Map<String, Object>> latestQuoteSnapshot(String exchange) {
        return Optional.ofNullable(latestQuoteByEx.get(exchange));
    }

    /** 시장별 최신 체결 스냅샷 (내부 분석용). */
    public Optional<Map<String, Object>> latestTradeSnapshot(String exchange) {
        return Optional.ofNullable(latestTradeByEx.get(exchange));
    }

    /** 시장별 최근 체결 버퍼 복사본 (내부 분석용, 최대 200건). */
    public List<Map<String, Object>> recentTrades(String exchange) {
        List<Map<String, Object>> buf = tradeBufferByEx.get(exchange);
        if (buf == null) return new ArrayList<>();
        synchronized (buf) {
            return new ArrayList<>(buf);
        }
    }

    /** 시장별 최신 회원사 스냅샷 (내부 분석용). */
    public Optional<Map<String, Object>> latestMemberSnapshot(String exchange) {
        return Optional.ofNullable(latestMemberByEx.get(exchange));
    }

    // ---- 연결 루프 ----

    private void runLoop() {
        long backoffMs = 5000;
        while (running.get() && enabled.get()) {
            try {
                if (!isMarketHours()) {
                    // 장 시간 외에는 대기 (NXT 애프터 20:00까지 커버)
                    Thread.sleep(60_000);
                    continue;
                }
                MysqlNamedLocks.Session candidate = locks.tryAcquire("kis-websocket");
                if (candidate == null) {
                    Thread.sleep(1000);
                    continue;
                }
                leadership = candidate;
                try {
                    // Quarantine gives a prior owner's watchdog time to abort on DB failure.
                    Thread.sleep(10_000);
                    if (candidate == leadership && candidate.valid() && running.get()) connectAndListen();
                } finally {
                    relinquishLeadership();
                }
                backoffMs = 5000; // 성공 후 리셋
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("WEMADE KIS websocket loop failed (isolated): {}", e.getClass().getSimpleName());
            }
            try {
                Thread.sleep(Math.min(backoffMs, 120_000));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            backoffMs = Math.min(backoffMs * 2, 120_000);
        }
        running.set(false);
    }

    /**
     * 평일 08:00~20:00 KST에만 연결 시도.
     * NXT PRE(08:00~08:50) ~ NXT AFTER(15:40~20:00) 전체 커버.
     */
    private boolean isMarketHours() {
        try {
            java.time.ZonedDateTime now =
                    java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
            int dow = now.getDayOfWeek().getValue();
            if (dow >= 6) return false;
            int hm = now.getHour() * 100 + now.getMinute();
            return hm >= 800 && hm <= 2000;
        } catch (Exception e) {
            return false;
        }
    }

    private void connectAndListen() throws Exception {
        if (!ownsLeadership()) return;
        String approvalKey = kisClient.websocketApprovalKey().orElse(null);
        if (approvalKey == null) {
            log.warn("WEMADE KIS websocket: approval key unavailable");
            return;
        }
        HttpClient client = HttpClient.newHttpClient();
        CompletableFuture<WebSocket> future = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(WS_URL), new WsListener());
        WebSocket connected = future.join();
        synchronized (this) {
            if (!ownsLeadership() || !running.get()) { connected.abort(); return; }
            webSocket = connected;
        }
        // 구독 요청 (승인키는 메시지 바디에만 포함, 로그 금지)
        // KRX + NXT + 통합 체결/호가/회원사
        sendSubscribe(TR_TRADE_KRX, "1");
        sendSubscribe(TR_TRADE_NXT, "1");
        sendSubscribe(TR_TRADE_UNI, "1");
        sendSubscribe(TR_QUOTE_KRX, "1");
        sendSubscribe(TR_QUOTE_NXT, "1");
        sendSubscribe(TR_QUOTE_UNI, "1");
        sendSubscribe(TR_MEMBER_KRX, "1");
        sendSubscribe(TR_MEMBER_NXT, "1");
        sendSubscribe(TR_MEMBER_UNI, "1");
        log.info("WEMADE KIS websocket connected, subscribed KRX/NXT/UNIFIED trade+quote+member");
        // 리스너 스레드에서 수신 처리. 이 스레드는 연결 유지용으로 대기한다.
        while (running.get() && enabled.get() && ownsLeadership() && webSocket != null && !webSocket.isOutputClosed() && isMarketHours()) {
            Thread.sleep(1000);
        }
        closeQuietly();
    }

    private void sendSubscribe(String trId, String trType) {
        try {
            if (!ownsLeadership() || webSocket == null) return;
            // 승인키는 kisClient에서 직접 읽어 메시지에 포함 (로그에 남기지 않는다)
            String approvalKey = kisClient.websocketApprovalKey().orElse("");
            String msg = "{\"header\":{\"approval_key\":\"" + approvalKey
                    + "\",\"custtype\":\"P\",\"tr_type\":\"" + trType
                    + "\",\"content-type\":\"utf-8\"},"
                    + "\"body\":{\"input\":{\"tr_id\":\"" + trId
                    + "\",\"tr_key\":\"" + SYMBOL + "\"}}}";
            webSocket.sendText(msg, true).join();
        } catch (Exception e) {
            log.warn("WEMADE KIS websocket subscribe failed (isolated): {}", e.getClass().getSimpleName());
        }
    }

    private boolean ownsLeadership() {
        MysqlNamedLocks.Session current = leadership;
        return current != null && current.valid();
    }

    private void checkLeadership() {
        MysqlNamedLocks.Session current = leadership;
        if (current != null && !current.valid()) relinquishLeadership();
    }

    private synchronized void relinquishLeadership() {
        MysqlNamedLocks.Session previous = leadership;
        leadership = null;
        closeQuietly(); // Abort external connection BEFORE RELEASE_LOCK / physical close.
        if (previous != null) previous.close();
    }

    private synchronized void closeQuietly() {
        try {
            if (webSocket != null) webSocket.abort();
        } catch (Exception ignored) {
        }
        webSocket = null;
    }

    // ---- 수신 리스너 ----

    private class WsListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            if (!ownsLeadership() || !running.get()) { ws.abort(); return; }
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (!ownsLeadership() || !running.get()) { ws.abort(); return null; }
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);
                handleMessage(msg);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            ws.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("WEMADE KIS websocket error (isolated): {}", error.getClass().getSimpleName());
        }
    }

    private void handleMessage(String msg) {
        try {
            if (msg == null || msg.isEmpty()) return;
            if (msg.charAt(0) == '{') {
                // 제어 메시지 (JSON)
                JsonNode root = objectMapper.readTree(msg);
                String trId = root.path("header").path("tr_id").asText("");
                if ("PINGPONG".equals(trId) && webSocket != null) {
                    try { webSocket.sendPong(ByteBuffer.allocate(0)).join(); }
                    catch (Exception ignored) { }
                }
                return;
            }
            // 실시간 데이터: "0|TR_ID|001|필드^필드^..." 또는 암호화 "1|..."
            String[] parts = msg.split("\\|", 4);
            if (parts.length < 4 || !"0".equals(parts[0])) return; // 암호화 데이터는 처리하지 않는다
            String trId = parts[1];
            String payload = parts[3];
            String exchange = TR_EXCHANGE.get(trId);
            String kind = TR_KIND.get(trId);
            if (exchange == null || kind == null) return; // 알 수 없는 TR은 무시
            switch (kind) {
                case "QUOTE" -> parseQuote(payload, exchange);
                case "TRADE" -> parseTrade(payload, exchange);
                case "MEMBER" -> parseMember(payload, exchange);
                default -> { }
            }
        } catch (Exception e) {
            log.warn("WEMADE KIS websocket parse failed (isolated): {}", e.getClass().getSimpleName());
        }
    }

    /**
     * KIS MARKET_CLS_CODE → 세션 매핑.
     * 1=프리(PRE), 2=정규(REGULAR), 3=애프터(AFTER), 5=종가(CLOSE).
     * 알 수 없는 값은 시간대 기준 폴백.
     */
    static String sessionFromMarketClsCode(String code) {
        if (code == null) return null;
        return switch (code.trim()) {
            case "1" -> "PRE";
            case "2" -> "REGULAR";
            case "3" -> "AFTER";
            case "5" -> "CLOSE";
            default -> null;
        };
    }

    /** 호가 파싱 (KRX/NXT/UNIFIED 공통 레이아웃, 인덱스 방어적 처리). */
    private void parseQuote(String payload, String exchange) {
        String[] f = payload.split("\\^", -1);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exchange", exchange);
            m.put("time", safe(f, 2));
            List<Long> askPrices = new ArrayList<>();
            List<Long> askResidues = new ArrayList<>();
            List<Long> bidPrices = new ArrayList<>();
            List<Long> bidResidues = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                askPrices.add(parseLongSafe(safe(f, 3 + i)));
                askResidues.add(parseLongSafe(safe(f, 13 + i)));
                bidPrices.add(parseLongSafe(safe(f, 23 + i)));
                bidResidues.add(parseLongSafe(safe(f, 33 + i)));
            }
            m.put("askPrices", askPrices);
            m.put("askResidues", askResidues);
            m.put("bidPrices", bidPrices);
            m.put("bidResidues", bidResidues);
            m.put("totalAskResidue", parseLongSafe(safe(f, 43)));
            m.put("totalBidResidue", parseLongSafe(safe(f, 44)));
            m.put("receivedAt", System.currentTimeMillis());
            latestQuoteByEx.put(exchange, m);
        } catch (Exception e) {
            log.warn("WEMADE KIS quote parse failed (isolated)");
        }
    }

    /**
     * 체결 파싱 (KRX/NXT/UNIFIED 공통 레이아웃).
     * MARKET_CLS_CODE는 공식 예제 컬럼 기준 마지막 필드 근처에 위치한다.
     * 필드 수가 다르면 방어적으로 빈 값을 사용한다.
     */
    private void parseTrade(String payload, String exchange) {
        String[] f = payload.split("\\^", -1);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exchange", exchange);
            m.put("time", safe(f, 1));
            m.put("price", parseLongSafe(safe(f, 2)));
            m.put("change", parseLongSafe(safe(f, 3)));
            m.put("changeRate", parseDoubleSafe(safe(f, 5)));
            m.put("volume", parseLongSafe(safe(f, 12)));
            m.put("accVolume", parseLongSafe(safe(f, 13)));
            m.put("accValue", parseLongSafe(safe(f, 14)));
            // 세션: MARKET_CLS_CODE 우선 (공식 예제상 마지막 컬럼)
            String session = sessionFromMarketClsCode(safe(f, f.length - 1));
            if (session == null) session = sessionFromMarketClsCode(findMarketClsCode(f));
            m.put("session", session);
            m.put("receivedAt", System.currentTimeMillis());
            latestTradeByEx.put(exchange, m);
            List<Map<String, Object>> buf =
                    tradeBufferByEx.computeIfAbsent(exchange, k -> new ArrayList<>());
            synchronized (buf) {
                buf.add(m);
                while (buf.size() > 200) buf.remove(0);
            }
        } catch (Exception e) {
            log.warn("WEMADE KIS trade parse failed (isolated)");
        }
    }

    /** 필드 배열에서 MARKET_CLS_CODE 후보(1자리 숫자)를 탐색한다. */
    private static String findMarketClsCode(String[] f) {
        // 뒤쪽 5개 필드에서 1자리 숫자 후보 탐색
        for (int i = f.length - 1; i >= Math.max(0, f.length - 6); i--) {
            String v = f[i].trim();
            if (v.length() == 1 && "1235".contains(v)) return v;
        }
        return "";
    }

    /**
     * 회원사 실시간 파싱 (H0STMBC0/H0NXMBC0/H0UNMBC0).
     * 상위 매도/매수 회원사 정보를 스냅샷으로 유지한다 (메모리만, DB 저장은 스케줄러가 수행).
     */
    private void parseMember(String payload, String exchange) {
        String[] f = payload.split("\\^", -1);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exchange", exchange);
            m.put("time", safe(f, 0));
            // 회원사명은 필드 구조상 가변적이므로 상위 5개 매도/매수를 방어적으로 파싱한다.
            // 정확한 인덱스는 KIS 실시간 회원사 명세 기준이며, 필드 수 부족 시 빈 값을 사용한다.
            List<Map<String, Object>> members = new ArrayList<>();
            // H0STMBC0 계열: [시간, 매도상위5(회원사명/매도량/비중...), 매수상위5...] 구조를 가정하고 방어적 파싱
            for (int i = 0; i < 5; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rank", i + 1);
                row.put("sellBroker", safe(f, 1 + i * 3));
                row.put("sellVol", parseLongSafe(safe(f, 2 + i * 3)));
                row.put("buyBroker", safe(f, 16 + i * 3));
                row.put("buyVol", parseLongSafe(safe(f, 17 + i * 3)));
                members.add(row);
            }
            m.put("members", members);
            m.put("receivedAt", System.currentTimeMillis());
            latestMemberByEx.put(exchange, m);
        } catch (Exception e) {
            log.warn("WEMADE KIS member parse failed (isolated)");
        }
    }

    private static String safe(String[] f, int i) {
        return (i >= 0 && i < f.length) ? f[i] : "";
    }

    private static Long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private static Double parseDoubleSafe(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }
}
