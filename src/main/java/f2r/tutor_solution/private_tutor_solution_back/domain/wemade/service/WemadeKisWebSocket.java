package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KIS 실시간 WebSocket 클라이언트 (실전: ws://ops.koreainvestment.com:21000).
 *
 * 구독 TR:
 * - H0STASP0: 주식호가 실시간
 * - H0STCNT0: 주식체결 실시간
 *
 * 원칙:
 * - 원시 틱 데이터는 메모리에만 유지하고 DB에 저장하지 않는다 (PRIVATE).
 * - 이상수급 탐지 서비스가 스냅샷을 읽어 파생 지표만 DB에 기록한다.
 * - 키/승인키는 절대 로그하지 않는다.
 * - 장 시간 외에는 연결하지 않고 조용히 대기한다.
 * - 연결 실패 시 지수 백오프로 재시도하되, 실패가 다른 기능을 막지 않는다.
 */
@Slf4j
@Component
public class WemadeKisWebSocket {

    private static final String WS_URL = "ws://ops.koreainvestment.com:21000";
    private static final String TR_QUOTE = "H0STASP0";
    private static final String TR_TRADE = "H0STCNT0";
    private static final String SYMBOL = "112040";

    private final WemadeKisClient kisClient;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private volatile WebSocket webSocket;

    /** 최신 호가 스냅샷 (내부 분석용) */
    private final AtomicReference<Map<String, Object>> latestQuote = new AtomicReference<>();
    /** 최신 체결 스냅샷 (내부 분석용) */
    private final AtomicReference<Map<String, Object>> latestTrade = new AtomicReference<>();
    /** 최근 체결 버퍼 (대량체결 탐지용, 최대 200건, 메모리만) */
    private final List<Map<String, Object>> tradeBuffer = new ArrayList<>();

    public WemadeKisWebSocket(WemadeKisClient kisClient, ObjectMapper objectMapper) {
        this.kisClient = kisClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        if (!kisClient.isConfigured()) {
            log.info("WEMADE KIS websocket disabled: keys not set (PENDING)");
            return;
        }
        enabled.set(true);
        Thread t = new Thread(this::runLoop, "wemade-kis-ws");
        t.setDaemon(true);
        t.start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        enabled.set(false);
        closeQuietly();
    }

    /** WebSocket 실시간 수신 여부 */
    public boolean isLive() {
        return enabled.get() && webSocket != null && !webSocket.isOutputClosed();
    }

    /** 최신 호가 스냅샷 (내부 분석용). 외부 API로 원시를 노출하지 않는다. */
    public Optional<Map<String, Object>> latestQuoteSnapshot() {
        return Optional.ofNullable(latestQuote.get());
    }

    /** 최신 체결 스냅샷 (내부 분석용). */
    public Optional<Map<String, Object>> latestTradeSnapshot() {
        return Optional.ofNullable(latestTrade.get());
    }

    /** 최근 체결 버퍼 복사본 (내부 분석용, 최대 200건). */
    public synchronized List<Map<String, Object>> recentTrades() {
        return new ArrayList<>(tradeBuffer);
    }

    // ---- 연결 루프 ----

    private void runLoop() {
        running.set(true);
        long backoffMs = 5000;
        while (running.get() && enabled.get()) {
            try {
                if (!isMarketHours()) {
                    // 장 시간 외에는 대기
                    Thread.sleep(60_000);
                    continue;
                }
                connectAndListen();
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

    /** 평일 08:50~15:40 KST에만 연결 시도 (장전 동시호가 포함). */
    private boolean isMarketHours() {
        try {
            java.time.ZonedDateTime now =
                    java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
            int dow = now.getDayOfWeek().getValue();
            if (dow >= 6) return false;
            int hm = now.getHour() * 100 + now.getMinute();
            return hm >= 850 && hm <= 1540;
        } catch (Exception e) {
            return false;
        }
    }

    private void connectAndListen() throws Exception {
        String approvalKey = kisClient.websocketApprovalKey().orElse(null);
        if (approvalKey == null) {
            log.warn("WEMADE KIS websocket: approval key unavailable");
            return;
        }
        HttpClient client = HttpClient.newHttpClient();
        CompletableFuture<WebSocket> future = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(WS_URL), new WsListener());
        webSocket = future.join();
        // 구독 요청 (승인키는 메시지 바디에만 포함, 로그 금지)
        sendSubscribe(TR_QUOTE, "1");
        sendSubscribe(TR_TRADE, "1");
        log.info("WEMADE KIS websocket connected, subscribed {}/{}", TR_QUOTE, TR_TRADE);
        // 리스너 스레드에서 수신 처리. 이 스레드는 연결 유지용으로 대기한다.
        while (running.get() && enabled.get() && !webSocket.isOutputClosed() && isMarketHours()) {
            Thread.sleep(10_000);
        }
        closeQuietly();
    }

    private void sendSubscribe(String trId, String trType) {
        try {
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

    private void closeQuietly() {
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
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
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
            // 실시간 데이터: "0|H0STASP0|001|필드^필드^..." 또는 암호화 "1|..."
            String[] parts = msg.split("\\|", 4);
            if (parts.length < 4 || !"0".equals(parts[0])) return; // 암호화 데이터는 처리하지 않는다
            String trId = parts[1];
            String payload = parts[3];
            if (TR_QUOTE.equals(trId)) {
                parseQuote(payload);
            } else if (TR_TRADE.equals(trId)) {
                parseTrade(payload);
            }
        } catch (Exception e) {
            log.warn("WEMADE KIS websocket parse failed (isolated): {}", e.getClass().getSimpleName());
        }
    }

    /** H0STASP0 호가 파싱 (인덱스 방어적 처리). */
    private void parseQuote(String payload) {
        String[] f = payload.split("\\^", -1);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
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
            latestQuote.set(m);
        } catch (Exception e) {
            log.warn("WEMADE KIS quote parse failed (isolated)");
        }
    }

    /** H0STCNT0 체결 파싱 (인덱스 방어적 처리). */
    private void parseTrade(String payload) {
        String[] f = payload.split("\\^", -1);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", safe(f, 1));
            m.put("price", parseLongSafe(safe(f, 2)));
            m.put("change", parseLongSafe(safe(f, 3)));
            m.put("changeRate", parseDoubleSafe(safe(f, 5)));
            m.put("volume", parseLongSafe(safe(f, 12)));
            m.put("accVolume", parseLongSafe(safe(f, 13)));
            m.put("accValue", parseLongSafe(safe(f, 14)));
            m.put("receivedAt", System.currentTimeMillis());
            latestTrade.set(m);
            synchronized (this) {
                tradeBuffer.add(m);
                while (tradeBuffer.size() > 200) tradeBuffer.remove(0);
            }
        } catch (Exception e) {
            log.warn("WEMADE KIS trade parse failed (isolated)");
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
