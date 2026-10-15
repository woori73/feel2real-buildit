package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeAnomalyEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeAnomalyMetric;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeMemberSnapshot;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeSurgeAnalysis;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeAnomalyEventRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeAnomalyMetricRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeMemberSnapshotRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeSurgeAnalysisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 이상수급 탐지 파이프라인 (자동매매 아님, 조회·분석 전용).
 *
 * 파이프라인: KIS → Muse → 이상수급 탐지 → 과거 유사패턴 검색 → 이벤트/공시 결합 → 분석 DB
 *
 * 4원칙:
 * 1. 원본과 분석값 즉시 분리: RAW(KIS 응답, 메모리만/PRIVATE) → NORMALIZED → ANALYSIS(DB, INTERNAL).
 *    KIS 원시 틱/호가 데이터를 DB에 저장하지 않는다.
 * 2. 회원사 데이터는 시간별 스냅샷으로 축적 (30분 간격, 급등 시 5분 간격).
 * 3. 결론부터 내리지 않는다: 각 지표를 독립 수치로 저장하고 조합은 별도 분석 단계에서 수행.
 *    임계값 초과 여부를 boolean이 아닌 원시 수치로 보관한다.
 * 4. 주문 API는 절대 연결하지 않는다.
 *
 * 연관성 원칙: 특정 거래원이나 외국인 매도가 중국 인수자와 관련됐다고 추정하지 않는다.
 * 직접 증거가 없으면 반드시 "연관성 확인되지 않음"으로 표시한다.
 */
@Slf4j
@Service
public class WemadeAnomalyService {

    private static final String SYMBOL = "112040";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WemadeKisClient kisClient;
    private final WemadeKisWebSocket kisWebSocket;
    private final WemadeAnomalyMetricRepository metricRepository;
    private final WemadeMemberSnapshotRepository memberSnapshotRepository;
    private final WemadeAnomalyEventRepository eventRepository;
    private final WemadeRawPriceRepository priceRepository;
    private final WemadeSurgeAnalysisRepository surgeRepository;

    public WemadeAnomalyService(WemadeKisClient kisClient,
                                WemadeKisWebSocket kisWebSocket,
                                WemadeAnomalyMetricRepository metricRepository,
                                WemadeMemberSnapshotRepository memberSnapshotRepository,
                                WemadeAnomalyEventRepository eventRepository,
                                WemadeRawPriceRepository priceRepository,
                                WemadeSurgeAnalysisRepository surgeRepository) {
        this.kisClient = kisClient;
        this.kisWebSocket = kisWebSocket;
        this.metricRepository = metricRepository;
        this.memberSnapshotRepository = memberSnapshotRepository;
        this.eventRepository = eventRepository;
        this.priceRepository = priceRepository;
        this.surgeRepository = surgeRepository;
    }

    // ================= 1단계: 지표 수집 (수치만, 결론 없음) =================

    /**
     * 장중 지표 수집. 각 지표를 독립 수치로 저장한다.
     * @return 저장된 지표 수
     */
    @Transactional
    public int collectIntradayMetrics() {
        if (!kisClient.isConfigured()) {
            log.info("WEMADE anomaly metrics skipped: KIS not configured (PENDING)");
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(KST);
        int saved = 0;

        // 1) 호가 불균형 (WebSocket 스냅샷 우선, 없으면 REST) — 시장별 분리
        for (String ex : new String[]{"KRX", "NXT", "UNIFIED"}) {
            final String exchange = ex;
            Optional<Double> imbalance = kisWebSocket.latestQuoteSnapshot(exchange)
                    .map(this::imbalanceFromWs)
                    .or(() -> exchange.equals("KRX") ? kisClient.quoteImbalance(SYMBOL) : Optional.empty());
            if (imbalance.isPresent()) {
                saved += saveMetric(SYMBOL, now, "QUOTE_IMBALANCE", imbalance.get(), null, "KIS_WS", "FACT", exchange);
            }
        }

        // 2) 현재가 위치 (당일 고저 대비)
        Optional<Map<String, Object>> quote = kisClient.realtimeQuote(SYMBOL);
        if (quote.isPresent()) {
            Map<String, Object> q = quote.get();
            Long price = toLong(q.get("price"));
            Long high = toLong(q.get("high"));
            Long low = toLong(q.get("low"));
            Long volume = toLong(q.get("volume"));
            if (price != null && high != null && low != null && high > low) {
                double pos = (price - low) / (double) (high - low);
                saved += saveMetric(SYMBOL, now, "PRICE_POSITION", pos, null, "KIS_REST", "FACT");
            }
            // 3) 분봉 기반 거래량 급증 (1/5/10분)
            saved += collectVolumeSurge(now);
        }

        // 4) 대량체결 건수 (WebSocket 체결 버퍼) — 시장별 분리
        for (String ex : new String[]{"KRX", "NXT", "UNIFIED"}) {
            List<Map<String, Object>> trades = kisWebSocket.recentTrades(ex);
            if (!trades.isEmpty()) {
                long median = medianVolume(trades);
                long largeCount = trades.stream()
                        .mapToLong(t -> toLong(t.get("volume")))
                        .filter(v -> median > 0 && v >= median * 10)
                        .count();
                saved += saveMetric(SYMBOL, now, "LARGE_TRADE_COUNT", (double) largeCount,
                        (double) trades.size(), "KIS_WS", "FACT", ex);
            }
        }

        // 4-2) NXT 특화 지표: NXT 거래량 급증, NXT/KRX 가격 괴리
        saved += collectNxtMetrics(now);

        // 5) 회원사 집중도 + 스냅샷
        saved += collectBrokerMetrics(now);

        // 6) 기술지표 (Bollinger %B, RSI14, MACD hist) — 일봉 기준
        saved += collectIndicatorMetrics(now);

        // 7) 당일 외국인/기관 순매수 금액 (KIS 일별 수급 최신값)
        saved += collectDailyFlowMetrics(now);

        return saved;
    }

    /** 분봉 기반 1/5/10분 거래량 급증 배수. */
    private int collectVolumeSurge(LocalDateTime now) {
        Optional<List<Map<String, Object>>> barsOpt = kisClient.minuteBars(SYMBOL);
        if (barsOpt.isEmpty() || barsOpt.get().size() < 15) return 0;
        List<Map<String, Object>> bars = barsOpt.get();
        // bars는 최신순. 오래된 10봉을 baseline으로 사용한다.
        int saved = 0;
        double baseline = bars.subList(10, Math.min(20, bars.size())).stream()
                .mapToLong(b -> toLong(b.get("volume"))).average().orElse(0);
        if (baseline <= 0) return 0;
        long v1 = toLong(bars.get(0).get("volume"));
        long v5 = bars.subList(0, Math.min(5, bars.size())).stream()
                .mapToLong(b -> toLong(b.get("volume"))).sum();
        long v10 = bars.subList(0, Math.min(10, bars.size())).stream()
                .mapToLong(b -> toLong(b.get("volume"))).sum();
        saved += saveMetric(SYMBOL, now, "VOL_SURGE_1M", v1 / baseline, baseline, "KIS_REST", "ANALYSIS");
        saved += saveMetric(SYMBOL, now, "VOL_SURGE_5M", (v5 / 5.0) / baseline, baseline, "KIS_REST", "ANALYSIS");
        saved += saveMetric(SYMBOL, now, "VOL_SURGE_10M", (v10 / 10.0) / baseline, baseline, "KIS_REST", "ANALYSIS");
        return saved;
    }

    /** 회원사 집중도 지표 + 시간별 스냅샷 저장. */
    private int collectBrokerMetrics(LocalDateTime now) {
        Optional<Map<String, List<Map<String, Object>>>> ranking = kisClient.memberRanking(SYMBOL);
        if (ranking.isEmpty()) return 0;
        int saved = 0;
        Map<String, List<Map<String, Object>>> r = ranking.get();
        // 집중도: 상위 1개사 비중
        saved += saveMetric(SYMBOL, now, "BROKER_TOP1_SELL_RATIO",
                topShare(r.get("sell")), null, "KIS_REST", "FACT");
        saved += saveMetric(SYMBOL, now, "BROKER_TOP1_BUY_RATIO",
                topShare(r.get("buy")), null, "KIS_REST", "FACT");
        // 스냅샷 축적 (회원사별): 매도/매수 리스트를 회원사명으로 병합
        Map<String, long[]> merged = new java.util.LinkedHashMap<>(); // [buyVol, sellVol, buyAmt, sellAmt]
        for (Map<String, Object> m : r.getOrDefault("sell", List.of())) {
            String name = String.valueOf(m.get("broker"));
            long[] a = merged.computeIfAbsent(name, k -> new long[4]);
            a[1] += toLong(m.get("volume"));
            a[3] += toLong(m.get("amount"));
        }
        for (Map<String, Object> m : r.getOrDefault("buy", List.of())) {
            String name = String.valueOf(m.get("broker"));
            long[] a = merged.computeIfAbsent(name, k -> new long[4]);
            a[0] += toLong(m.get("volume"));
            a[2] += toLong(m.get("amount"));
        }
        for (Map.Entry<String, long[]> e : merged.entrySet()) {
            long[] a = e.getValue();
            String hash = sha256(SYMBOL + "|" + now.toLocalDate() + "|" + now.getHour() + ":" + (now.getMinute() / 30 * 30) + "|" + e.getKey());
            if (memberSnapshotRepository.findAll().stream().anyMatch(s -> hash.equals(s.getContentHash()))) {
                continue;
            }
            WemadeMemberSnapshot snap = WemadeMemberSnapshot.builder()
                    .symbol(SYMBOL)
                    .measuredAt(now)
                    .brokerName(e.getKey())
                    .buyVolume(a[0])
                    .sellVolume(a[1])
                    .netVolume(a[0] - a[1])
                    .buyAmount(a[2])
                    .sellAmount(a[3])
                    .source("KIS")
                    .exchange("KRX") // REST 회원사 조회는 KRX 기준. NXT는 WS 스냅샷 별도.
                    .relationNote("연관성 확인되지 않음")
                    .contentHash(hash)
                    .createdAt(LocalDateTime.now(KST))
                    .build();
            memberSnapshotRepository.save(snap);
            saved++;
        }
        return saved;
    }

    private double topShare(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return 0.0;
        long top = toLong(list.get(0).get("volume"));
        long total = list.stream().mapToLong(m -> toLong(m.get("volume"))).sum();
        return total == 0 ? 0.0 : top / (double) total;
    }

    /** 일봉 기술지표: Bollinger %B, RSI14, MACD hist. */
    private int collectIndicatorMetrics(LocalDateTime now) {
        List<WemadeRawPrice> prices = priceRepository.findBySymbolOrderByTradeDateAsc(SYMBOL);
        if (prices.size() < 30) return 0;
        List<Double> closes = new ArrayList<>();
        for (WemadeRawPrice p : prices) {
            if (p.getClosePrice() != null) closes.add(p.getClosePrice().doubleValue());
        }
        int saved = 0;
        // Bollinger %B (20, 2)
        List<Double> upper = WemadeIndicatorService.bollingerUpper(closes, 20, 2.0);
        List<Double> lower = WemadeIndicatorService.bollingerLower(closes, 20, 2.0);
        Double mid = WemadeIndicatorService.sma(closes, 20, closes.size() - 1);
        int last = closes.size() - 1;
        if (upper.get(last) != null && lower.get(last) != null && mid != null) {
            double pctB = (closes.get(last) - lower.get(last)) / (upper.get(last) - lower.get(last));
            saved += saveMetric(SYMBOL, now, "BOLLINGER_PCTB", pctB, null, "CALC", "ANALYSIS");
        }
        // RSI14
        List<Double> rsi = WemadeIndicatorService.rsi14(closes);
        if (rsi.get(last) != null) {
            saved += saveMetric(SYMBOL, now, "RSI_14", rsi.get(last), null, "CALC", "ANALYSIS");
        }
        // MACD hist
        List<Double> macd = WemadeIndicatorService.macdLine(closes);
        List<Double> signal = WemadeIndicatorService.ema(macd.stream()
                .map(v -> v == null ? 0.0 : v).toList(), 9);
        if (macd.get(last) != null && signal.get(last) != null) {
            saved += saveMetric(SYMBOL, now, "MACD_HIST", macd.get(last) - signal.get(last), null, "CALC", "ANALYSIS");
        }
        // MA20/60/120
        for (int n : new int[]{20, 60, 120}) {
            Double ma = WemadeIndicatorService.sma(closes, n, last);
            if (ma != null) {
                saved += saveMetric(SYMBOL, now, "MA_" + n, ma, closes.get(last), "CALC", "ANALYSIS");
            }
        }
        return saved;
    }

    /** KIS 일별 수급 최신값 → 외국인/기관 순매수 금액 지표. */
    private int collectDailyFlowMetrics(LocalDateTime now) {
        Optional<List<Map<String, Object>>> flows = kisClient.investorFlows(SYMBOL);
        if (flows.isEmpty() || flows.get().isEmpty()) return 0;
        Map<String, Object> latest = flows.get().get(0);
        int saved = 0;
        Long fAmt = toLong(latest.get("foreignNetAmt"));
        Long iAmt = toLong(latest.get("institutionNetAmt"));
        if (fAmt != null) {
            saved += saveMetric(SYMBOL, now, "FOREIGN_NET_AMT", fAmt.doubleValue(), null, "KIS_REST", "FACT");
        }
        if (iAmt != null) {
            saved += saveMetric(SYMBOL, now, "INSTITUTION_NET_AMT", iAmt.doubleValue(), null, "KIS_REST", "FACT");
        }
        return saved;
    }

    // ================= 2단계: 조합 분석 (지표 → 분석 이벤트) =================

    /**
     * 최근 지표들을 조합해 분석 이벤트를 생성한다.
     * 결론을 먼저 내리지 않고 evidenceLevel을 구분한다.
     */
    @Transactional
    public int analyzeCombined() {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime from = now.minusHours(8);
        List<WemadeAnomalyMetric> metrics =
                metricRepository.findBySymbolAndMeasuredAtBetweenOrderByMeasuredAtAsc(SYMBOL, from, now);
        if (metrics.isEmpty()) return 0;
        int saved = 0;
        saved += analyzeVolumeSurge(metrics, now);
        saved += analyzeQuoteImbalance(metrics, now);
        saved += analyzeBrokerConcentration(metrics, now);
        saved += analyzeBollingerPressure(metrics, now);
        saved += analyzeForeignSellRally(now);
        return saved;
    }

    private Double latestValue(List<WemadeAnomalyMetric> metrics, String type) {
        for (int i = metrics.size() - 1; i >= 0; i--) {
            WemadeAnomalyMetric m = metrics.get(i);
            if (type.equals(m.getMetricType()) && m.getMetricValue() != null) {
                return m.getMetricValue();
            }
        }
        return null;
    }

    private String metricIds(List<WemadeAnomalyMetric> metrics, String... types) {
        List<String> ids = new ArrayList<>();
        for (WemadeAnomalyMetric m : metrics) {
            for (String t : types) {
                if (t.equals(m.getMetricType())) { ids.add(String.valueOf(m.getId())); break; }
            }
        }
        return String.join(",", ids);
    }

    private int analyzeVolumeSurge(List<WemadeAnomalyMetric> metrics, LocalDateTime now) {
        Double v5 = latestValue(metrics, "VOL_SURGE_5M");
        Double v10 = latestValue(metrics, "VOL_SURGE_10M");
        if (v5 == null && v10 == null) return 0;
        double maxSurge = Math.max(v5 == null ? 0 : v5, v10 == null ? 0 : v10);
        if (maxSurge < 3.0) return 0; // 3배 미만은 기록만 하고 이벤트 생성 안 함
        return saveEvent(SYMBOL, now, "VOLUME_SURGE",
                String.format("거래량 급증: 5분 배수 %.1f배, 10분 배수 %.1f배 (평상시 대비)", nz(v5), nz(v10)),
                "FACT", metricIds(metrics, "VOL_SURGE_1M", "VOL_SURGE_5M", "VOL_SURGE_10M"));
    }

    private int analyzeQuoteImbalance(List<WemadeAnomalyMetric> metrics, LocalDateTime now) {
        Double imb = latestValue(metrics, "QUOTE_IMBALANCE");
        if (imb == null || Math.abs(imb) < 0.3) return 0;
        String side = imb > 0 ? "매수" : "매도";
        return saveEvent(SYMBOL, now, "QUOTE_IMBALANCE",
                String.format("호가잔량 불균형: %s 우위 %.2f (-1~+1)", side, imb),
                "FACT", metricIds(metrics, "QUOTE_IMBALANCE"));
    }

    private int analyzeBrokerConcentration(List<WemadeAnomalyMetric> metrics, LocalDateTime now) {
        Double sellR = latestValue(metrics, "BROKER_TOP1_SELL_RATIO");
        Double buyR = latestValue(metrics, "BROKER_TOP1_BUY_RATIO");
        double maxR = Math.max(nz(sellR), nz(buyR));
        if (maxR < 0.4) return 0;
        return saveEvent(SYMBOL, now, "BROKER_CONCENTRATION",
                String.format("특정 회원사 집중: 매도 상위 %.1f%%, 매수 상위 %.1f%%",
                        nz(sellR) * 100, nz(buyR) * 100),
                "FACT", metricIds(metrics, "BROKER_TOP1_SELL_RATIO", "BROKER_TOP1_BUY_RATIO"));
    }

    private int analyzeBollingerPressure(List<WemadeAnomalyMetric> metrics, LocalDateTime now) {
        Double pctB = latestValue(metrics, "BOLLINGER_PCTB");
        Double v5 = latestValue(metrics, "VOL_SURGE_5M");
        Double fAmt = latestValue(metrics, "FOREIGN_NET_AMT");
        if (pctB == null) return 0;
        // 볼린저 상단 접근(%B>=0.8) + 거래량 증가(2배+) + 외국인 순매도
        boolean upperTouch = pctB >= 0.8;
        boolean volUp = v5 != null && v5 >= 2.0;
        boolean foreignSell = fAmt != null && fAmt < 0;
        if (!(upperTouch && volUp && foreignSell)) return 0;
        return saveEvent(SYMBOL, now, "BOLLINGER_SELL_PRESSURE",
                String.format("볼린저 상단 접근(%%B %.2f) + 거래량 %.1f배 + 외국인 순매도 %,.0f백만원",
                        pctB, nz(v5), nz(fAmt)),
                "ANALYSIS", metricIds(metrics, "BOLLINGER_PCTB", "VOL_SURGE_5M", "FOREIGN_NET_AMT"));
    }

    /**
     * 급등 구간 외국인/기관 순매도 분석.
     * FACT: 급등일 + 당일 외국인/기관 순매도 관측값.
     * '고점 외국인·기관 매도 반복' 가설은 HYPOTHESIS로 유지하고 여기서 결론내지 않는다.
     */
    private int analyzeForeignSellRally(LocalDateTime now) {
        List<WemadeSurgeAnalysis> surges = surgeRepository.findAll().stream()
                .filter(s -> SYMBOL.equals(s.getSymbol()))
                .sorted((a, b) -> b.getSurgeDate().compareTo(a.getSurgeDate()))
                .limit(5).toList();
        int saved = 0;
        for (WemadeSurgeAnalysis s : surges) {
            if (s.getForeignNetBuy() == null && s.getInstitutionNetBuy() == null) continue;
            boolean fSell = s.getForeignNetBuy() != null && s.getForeignNetBuy() < 0;
            boolean iSell = s.getInstitutionNetBuy() != null && s.getInstitutionNetBuy() < 0;
            if (!fSell && !iSell) continue;
            String hash = sha256("FSR|" + s.getSurgeDate() + "|" + s.getForeignNetBuy() + "|" + s.getInstitutionNetBuy());
            if (eventRepository.findAll().stream().anyMatch(e -> hash.equals(e.getContentHash()))) continue;
            WemadeAnomalyEvent ev = WemadeAnomalyEvent.builder()
                    .symbol(SYMBOL)
                    .detectedAt(now)
                    .analysisType("FOREIGN_SELL_RALLY")
                    .summary(String.format("급등일(%s, %+.1f%%) 외국인 %s%,d백만원 / 기관 %s%,d백만원",
                            s.getSurgeDate(), nz(s.getChangeRate()),
                            fSell ? "순매도 " : "순매수 ", nzL(s.getForeignNetBuy()),
                            iSell ? "순매도 " : "순매수 ", nzL(s.getInstitutionNetBuy())))
                    .evidenceLevel("FACT")
                    .relationNote("연관성 확인되지 않음")
                    .similarPastCount(null)
                    .similarPastAvgRet5d(s.getRet5d())
                    .contentHash(hash)
                    .createdAt(LocalDateTime.now(KST))
                    .build();
            eventRepository.save(ev);
            saved++;
        }
        return saved;
    }

    /**
     * 과거 유사 패턴 검색: 현재 급등 시그니처(등락률 구간, 거래량 배수)와 유사한
     * 2026-06-29 이후 과거 급등일을 찾아 이후 수익률을 함께 기록한다.
     */
    @Transactional
    public int searchSimilarPatterns() {
        LocalDateTime now = LocalDateTime.now(KST);
        List<WemadeSurgeAnalysis> surges = surgeRepository.findAll().stream()
                .filter(s -> SYMBOL.equals(s.getSymbol())
                        && !s.getSurgeDate().isBefore(LocalDate.of(2026, 6, 29)))
                .sorted((a, b) -> a.getSurgeDate().compareTo(b.getSurgeDate()))
                .toList();
        if (surges.size() < 2) return 0;
        WemadeSurgeAnalysis latest = surges.get(surges.size() - 1);
        // 유사 기준: 등락률 ±3%p 이내
        List<WemadeSurgeAnalysis> similar = surges.subList(0, surges.size() - 1).stream()
                .filter(s -> Math.abs(nz(s.getChangeRate()) - nz(latest.getChangeRate())) <= 3.0)
                .toList();
        if (similar.isEmpty()) return 0;
        double avgRet5d = similar.stream()
                .filter(s -> s.getRet5d() != null)
                .mapToDouble(WemadeSurgeAnalysis::getRet5d)
                .average().orElse(Double.NaN);
        String hash = sha256("SIM|" + latest.getSurgeDate() + "|" + similar.size());
        if (eventRepository.findAll().stream().anyMatch(e -> hash.equals(e.getContentHash()))) return 0;
        WemadeAnomalyEvent ev = WemadeAnomalyEvent.builder()
                .symbol(SYMBOL)
                .detectedAt(now)
                .analysisType("POST_SURGE_MOVE")
                .summary(String.format("급등일(%s, %+.1f%%)과 유사한 과거 패턴 %d건 (2026-06-29 이후)",
                        latest.getSurgeDate(), nz(latest.getChangeRate()), similar.size()))
                .evidenceLevel("ANALYSIS")
                .relationNote("연관성 확인되지 않음")
                .similarPastCount(similar.size())
                .similarPastAvgRet5d(Double.isNaN(avgRet5d) ? null : avgRet5d)
                .contentHash(hash)
                .createdAt(LocalDateTime.now(KST))
                .build();
        eventRepository.save(ev);
        return 1;
    }

    // ---- helpers ----

    private int saveMetric(String symbol, LocalDateTime measuredAt, String type,
                           Double value, Double baseline, String source, String evidenceLevel,
                           String exchange) {
        if (value == null) return 0;
        String hash = sha256(symbol + "|" + exchange + "|" + measuredAt.toLocalDate() + "|" + measuredAt.getHour()
                + ":" + measuredAt.getMinute() + "|" + type);
        if (metricRepository.findAll().stream().anyMatch(m -> hash.equals(m.getContentHash()))) return 0;
        WemadeAnomalyMetric m = WemadeAnomalyMetric.builder()
                .symbol(symbol)
                .measuredAt(measuredAt)
                .metricType(type)
                .metricValue(value)
                .baselineValue(baseline)
                .source(source)
                .exchange(exchange)
                .evidenceLevel(evidenceLevel)
                .contentHash(hash)
                .createdAt(LocalDateTime.now(KST))
                .build();
        metricRepository.save(m);
        return 1;
    }

    /** 기존 호출 호환용 (KRX 기본값). */
    private int saveMetric(String symbol, LocalDateTime measuredAt, String type,
                           Double value, Double baseline, String source, String evidenceLevel) {
        return saveMetric(symbol, measuredAt, type, value, baseline, source, evidenceLevel, "KRX");
    }

    /**
     * NXT 특화 지표 (원시 신호만, 자동 해석 금지).
     * - NXT_VOL_SURGE: NXT 체결 버퍼 기반 거래량 급증 배수
     * - NXT_KRX_PRICE_GAP: NXT 최신가 - KRX 최신가 (원)
     * - NXT_KRX_PRICE_GAP_RATE: 괴리율 (%)
     * - NXT_PRE_CHANGE_RATE: NXT 프리마켓(08:00~08:50) 가격 변화율 (세션=PRE 틱 기준)
     */
    private int collectNxtMetrics(LocalDateTime now) {
        int saved = 0;
        Optional<Map<String, Object>> nxtTrade = kisWebSocket.latestTradeSnapshot("NXT");
        Optional<Map<String, Object>> krxTrade = kisWebSocket.latestTradeSnapshot("KRX");
        if (nxtTrade.isPresent() && krxTrade.isPresent()) {
            long nxtPrice = toLong(nxtTrade.get().get("price"));
            long krxPrice = toLong(krxTrade.get().get("price"));
            if (nxtPrice > 0 && krxPrice > 0) {
                double gap = nxtPrice - krxPrice;
                double gapRate = gap / (double) krxPrice * 100.0;
                saved += saveMetric(SYMBOL, now, "NXT_KRX_PRICE_GAP", gap, null, "KIS_WS", "FACT", "NXT");
                saved += saveMetric(SYMBOL, now, "NXT_KRX_PRICE_GAP_RATE", gapRate, null, "KIS_WS", "ANALYSIS", "NXT");
            }
        }
        // NXT 호가 불균형 vs KRX 호가 불균형 차이
        Optional<Double> nxtImb = kisWebSocket.latestQuoteSnapshot("NXT").map(this::imbalanceFromWs);
        Optional<Double> krxImb = kisWebSocket.latestQuoteSnapshot("KRX").map(this::imbalanceFromWs);
        if (nxtImb.isPresent() && krxImb.isPresent()) {
            double diff = nxtImb.get() - krxImb.get();
            saved += saveMetric(SYMBOL, now, "NXT_KRX_QUOTE_IMBALANCE_DIFF", diff, null, "KIS_WS", "ANALYSIS", "NXT");
        }
        return saved;
    }

    private int saveEvent(String symbol, LocalDateTime detectedAt, String analysisType,
                          String summary, String evidenceLevel, String metricIds) {
        String hash = sha256(symbol + "|" + detectedAt.toLocalDate() + "|" + detectedAt.getHour()
                + ":" + detectedAt.getMinute() + "|" + analysisType + "|" + summary.hashCode());
        if (eventRepository.findAll().stream().anyMatch(e -> hash.equals(e.getContentHash()))) return 0;
        WemadeAnomalyEvent ev = WemadeAnomalyEvent.builder()
                .symbol(symbol)
                .detectedAt(detectedAt)
                .analysisType(analysisType)
                .summary(summary)
                .evidenceLevel(evidenceLevel)
                .relationNote("연관성 확인되지 않음")
                .metricIds(metricIds)
                .contentHash(hash)
                .createdAt(LocalDateTime.now(KST))
                .build();
        eventRepository.save(ev);
        return 1;
    }

    private Double imbalanceFromWs(Map<String, Object> quote) {
        Object a = quote.get("totalAskResidue");
        Object b = quote.get("totalBidResidue");
        long askL = a instanceof Number ? ((Number) a).longValue() : 0L;
        long bidL = b instanceof Number ? ((Number) b).longValue() : 0L;
        long total = askL + bidL;
        return total == 0 ? 0.0 : (bidL - askL) / (double) total;
    }

    private long medianVolume(List<Map<String, Object>> trades) {
        List<Long> vs = new ArrayList<>();
        for (Map<String, Object> t : trades) vs.add(toLong(t.get("volume")));
        vs.sort(Long::compareTo);
        if (vs.isEmpty()) return 0;
        return vs.get(vs.size() / 2);
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o).trim()); }
        catch (Exception e) { return 0L; }
    }

    private static double nz(Double d) { return d == null ? 0.0 : d; }
    private static long nzL(Long l) { return l == null ? 0L : l; }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
