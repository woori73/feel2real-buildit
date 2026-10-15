package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.WemadeDealConstants;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto.WemadeEventView;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto.WemadePricePoint;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeChinaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeEventService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeKisClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadePriceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeSurgeAnalysisService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeAnomalyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeKisWebSocket;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 위메이드 개인주주 분석 공개 API.
 * 읽기 전용. 자동매매/추천/목표주가 기능 없음.
 */
@RestController
@RequestMapping("/api/wemade")
@RequiredArgsConstructor
public class WemadeApiController {

    private final WemadePriceService priceService;
    private final WemadeEventService eventService;
    private final WemadeChinaService chinaService;
    private final WemadeInvestorFlowRepository flowRepository;
    private final WemadeFactCheckRepository factCheckRepository;
    private final WemadeGameTrackingRepository gameTrackingRepository;
    private final WemadeCommunityPostRepository communityPostRepository;
    private final WemadeDisclosureRepository disclosureRepository;
    private final WemadeCollectionRunRepository runRepository;
    private final WemadeKisClient kisClient;
    private final WemadeSurgeAnalysisService surgeAnalysisService;
    private final WemadeAnomalyService anomalyService;
    private final WemadeKisWebSocket kisWebSocket;
    private final WemadeAnomalyEventRepository anomalyEventRepository;
    private final WemadeAnomalyMetricRepository anomalyMetricRepository;
    private final WemadeMemberSnapshotRepository memberSnapshotRepository;

    /** 종목 메타: 112040/101730(KRW), 002517(CNY). */
    @GetMapping("/symbols")
    public ResponseEntity<List<Map<String, String>>> symbols() {
        return ResponseEntity.ok(WemadePriceService.SYMBOLS);
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @RequestParam(defaultValue = "112040") String symbol) {
        return ResponseEntity.ok(priceService.overview(symbol));
    }

    @GetMapping("/prices")
    public ResponseEntity<List<WemadePricePoint>> prices(
            @RequestParam(defaultValue = "112040") String symbol,
            @RequestParam(defaultValue = "180") int days) {
        return ResponseEntity.ok(priceService.recentWithIndicators(symbol, Math.min(Math.max(days, 5), 2000)));
    }

    /**
     * 3종목 정규화 수익률 비교.
     * base(기본 2026-06-30 SPA 체결일) 종가=100. 통화(KRW/CNY)·거래시간대 차이는 정규화로 제거됨.
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare(
            @RequestParam(defaultValue = "112040,101730,002517") String symbols,
            @RequestParam(defaultValue = "2026-06-30") String base,
            @RequestParam(defaultValue = "120") int days) {
        List<String> list = Arrays.stream(symbols.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
        return ResponseEntity.ok(priceService.compareNormalized(list, base, Math.min(Math.max(days, 10), 500)));
    }

    @GetMapping("/flows")
    public ResponseEntity<Map<String, Object>> flows(@RequestParam(defaultValue = "60") int days) {
        List<WemadeInvestorFlow> rows = flowRepository.findAll().stream()
                .sorted(Comparator.comparing(WemadeInvestorFlow::getTradeDate)).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            m.put("hasData", false);
            m.put("message", "데이터 없음");
            return ResponseEntity.ok(m);
        }
        m.put("hasData", true);
        int n = Math.min(days, rows.size());
        List<WemadeInvestorFlow> win = rows.subList(rows.size() - n, rows.size());
        List<Map<String, Object>> list = new ArrayList<>();
        for (WemadeInvestorFlow r : win) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("date", r.getTradeDate().toString());
            o.put("investorType", r.getInvestorType());
            o.put("netBuyAmount", r.getNetBuyAmount());
            list.add(o);
        }
        m.put("rows", list);
        m.put("summary", summarize(win));
        return ResponseEntity.ok(m);
    }

    private Map<String, Object> summarize(List<WemadeInvestorFlow> win) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("d1", netOf(win, 1));
        s.put("d5", netOf(win, 5));
        s.put("d20", netOf(win, 20));
        s.put("d60", netOf(win, 60));
        return s;
    }

    /** 해당 기간 데이터가 부족하면 null (0으로 표시하지 않는다). */
    private Map<String, Long> netOf(List<WemadeInvestorFlow> win, int days) {
        if (win.size() < days) return null;
        List<WemadeInvestorFlow> sub = win.subList(win.size() - days, win.size());
        Map<String, Long> m = new LinkedHashMap<>();
        for (String t : List.of("FOREIGN", "INSTITUTION", "RETAIL")) {
            long sum = sub.stream().filter(r -> t.equals(r.getInvestorType()))
                    .mapToLong(r -> r.getNetBuyAmount() == null ? 0 : r.getNetBuyAmount()).sum();
            boolean any = sub.stream().anyMatch(r -> t.equals(r.getInvestorType()) && r.getNetBuyAmount() != null);
            m.put(t.toLowerCase(), any ? sum : null);
        }
        return m;
    }

    @GetMapping("/events")
    public ResponseEntity<List<WemadeEventView>> events() {
        return ResponseEntity.ok(eventService.eventsWithPriceReaction());
    }

    @GetMapping("/fact-checks")
    public ResponseEntity<List<WemadeFactCheck>> factChecks() {
        return ResponseEntity.ok(factCheckRepository.findAllByOrderByClaimKeyAsc());
    }

    @GetMapping("/game-tracking")
    public ResponseEntity<Map<String, List<WemadeGameTracking>>> gameTracking() {
        Map<String, List<WemadeGameTracking>> m = new LinkedHashMap<>();
        m.put("official", new ArrayList<>());
        m.put("press", new ArrayList<>());
        m.put("community", new ArrayList<>());
        for (WemadeGameTracking g : gameTrackingRepository.findAllByOrderByTrackedAtDesc()) {
            String k = g.getSourceType() == null ? "community" : g.getSourceType().toLowerCase();
            m.computeIfAbsent(k, x -> new ArrayList<>()).add(g);
        }
        return ResponseEntity.ok(m);
    }

    @GetMapping("/community")
    public ResponseEntity<List<WemadeCommunityPost>> community() {
        return ResponseEntity.ok(communityPostRepository.findAllByOrderByPostedAtDesc());
    }

    @GetMapping("/disclosures")
    public ResponseEntity<List<WemadeDisclosure>> disclosures() {
        return ResponseEntity.ok(disclosureRepository.findAllByOrderByDisclosedAtDesc());
    }

    /** 중국 현지 정보: 킹넷 공식 공시. */
    @GetMapping("/china/disclosures")
    public ResponseEntity<List<WemadeChinaDisclosure>> chinaDisclosures() {
        return ResponseEntity.ok(chinaService.latestDisclosures());
    }

    /** 중국 현지 정보: 중국 뉴스 (원문+번역). */
    @GetMapping("/china/news")
    public ResponseEntity<List<WemadeChinaNews>> chinaNews() {
        return ResponseEntity.ok(chinaService.latestNews());
    }

    /** 중국 현지 정보 최신 1건 (첫 화면 카드용). */
    @GetMapping("/china/latest")
    public ResponseEntity<Map<String, Object>> chinaLatest() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<WemadeChinaDisclosure> d = chinaService.latestDisclosures();
        List<WemadeChinaNews> n = chinaService.latestNews();
        m.put("disclosure", d.isEmpty() ? null : d.get(0));
        m.put("news", n.isEmpty() ? null : n.get(0));
        return ResponseEntity.ok(m);
    }

    @GetMapping("/collection/status")
    public ResponseEntity<List<WemadeCollectionRun>> collectionStatus() {
        return ResponseEntity.ok(runRepository.findTop20ByOrderByStartedAtDesc());
    }

    /**
     * 실시간 현재가 1건.
     * 1순위: KIS Open API 실시간 (KIS_APP_KEY/SECRET 설정 시).
     * 폴백: pykrx/DB 지연 시세 (장마감 기준).
     *
     * 약관 주의: KIS 시세정보 서비스 이용약관 제7조는 승인 없는 시세정보의
     * 제3자 제공·재판매를 금지한다. 약관상 공개 범위 확인 전까지 실시간 시세는
     * 내부 분석용으로만 사용하고 외부 표시 범위를 제한한다
     * (현재가 1건 + 배지. 원시 호가/체결 전량은 제공하지 않는다).
     */
    @GetMapping("/quote/realtime")
    public ResponseEntity<Map<String, Object>> realtimeQuote(
            @RequestParam(defaultValue = "112040") String symbol) {
        Optional<Map<String, Object>> kis = kisClient.realtimeQuote(symbol);
        if (kis.isPresent()) {
            Map<String, Object> m = new LinkedHashMap<>(kis.get());
            m.put("badge", "실시간");
            m.put("redistributionRestricted", true);
            m.put("termsNote", "KIS 약관상 제3자 재배포 제한 — 공개 범위 확인 전까지 내부 분석용");
            return ResponseEntity.ok(m);
        }
        // 폴백: DB 최신 일봉 (지연 시세)
        Map<String, Object> m = new LinkedHashMap<>(priceService.overview(symbol));
        m.put("realtime", false);
        m.put("badge", "지연 시세");
        m.put("source", "PYKRX_DELAYED");
        return ResponseEntity.ok(m);
    }

    /** 급등일 수급 분석 목록. */
    @GetMapping("/surge-analysis")
    public ResponseEntity<List<WemadeSurgeAnalysis>> surgeAnalysis(
            @RequestParam(defaultValue = "112040") String symbol) {
        return ResponseEntity.ok(
                surgeAnalysisService == null ? List.of()
                        : surgeAnalysisService.list(symbol));
    }

    /** '고점 외국인·기관 매도 반복' 가설 검증 상태. */
    @GetMapping("/surge-analysis/hypothesis")
    public ResponseEntity<Map<String, Object>> surgeHypothesis(
            @RequestParam(defaultValue = "112040") String symbol) {
        return ResponseEntity.ok(surgeAnalysisService.hypothesisSummary(symbol));
    }

    /**
     * KIS 피드 상태 (원시 데이터 노출 없음).
     * realtime/ws/investor/member 각 피드의 연결 상태만 반환한다.
     */
    @GetMapping("/kis/status")
    public ResponseEntity<Map<String, Object>> kisStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", kisClient.isConfigured());
        m.put("websocketLive", kisWebSocket.isLive());
        m.put("subscriptions", List.of(
                "H0STCNT0/H0STASP0/H0STMBC0 (KRX)",
                "H0NXCNT0/H0NXASP0/H0NXMBC0 (NXT)",
                "H0UNCNT0/H0UNASP0/H0UNMBC0 (UNIFIED)"));
        m.put("note", "KIS 원시 시세/호가 데이터는 PRIVATE/INTERNAL — 외부에 재배포하지 않음");
        return ResponseEntity.ok(m);
    }

    /**
     * 이상수급 분석 이벤트 목록 (파생 분석값만, 원시 KIS 틱 미포함).
     * evidenceLevel: FACT(관측) / ANALYSIS(계산) / HYPOTHESIS(미확인 가설).
     * relationNote가 "연관성 확인되지 않음"이 아닌 경우는 직접 증거가 있는 경우뿐이다.
     */
    @GetMapping("/anomaly/events")
    public ResponseEntity<List<WemadeAnomalyEvent>> anomalyEvents(
            @RequestParam(defaultValue = "112040") String symbol,
            @RequestParam(required = false) String exchange) {
        if (exchange != null && !exchange.isBlank()) {
            return ResponseEntity.ok(anomalyEventRepository.findBySymbolAndExchangeOrderByDetectedAtDesc(symbol, exchange));
        }
        return ResponseEntity.ok(anomalyEventRepository.findBySymbolOrderByDetectedAtDesc(symbol));
    }

    /**
     * 이상수급 지표 수치 (원시 수치가 아닌 파생 지표).
     * 각 지표를 독립 수치로 반환하며 결론은 포함하지 않는다.
     */
    @GetMapping("/anomaly/metrics")
    public ResponseEntity<List<WemadeAnomalyMetric>> anomalyMetrics(
            @RequestParam(defaultValue = "112040") String symbol,
            @RequestParam(defaultValue = "1") int hours,
            @RequestParam(required = false) String exchange) {
        java.time.LocalDateTime to = java.time.LocalDateTime.now();
        java.time.LocalDateTime from = to.minusHours(Math.min(Math.max(hours, 1), 72));
        if (exchange != null && !exchange.isBlank()) {
            return ResponseEntity.ok(
                    anomalyMetricRepository.findBySymbolAndExchangeAndMeasuredAtBetweenOrderByMeasuredAtAsc(symbol, exchange, from, to));
        }
        return ResponseEntity.ok(
                anomalyMetricRepository.findBySymbolAndMeasuredAtBetweenOrderByMeasuredAtAsc(symbol, from, to));
    }

    /**
     * 회원사 시간별 스냅샷 (파생 집계값).
     * 특정 거래원과 중국 인수자의 연관성은 직접 증거 없이 추정하지 않는다.
     */
    @GetMapping("/anomaly/members")
    public ResponseEntity<List<WemadeMemberSnapshot>> memberSnapshots(
            @RequestParam(defaultValue = "112040") String symbol,
            @RequestParam(defaultValue = "1") int days,
            @RequestParam(required = false) String exchange) {
        java.time.LocalDateTime to = java.time.LocalDateTime.now();
        java.time.LocalDateTime from = to.minusDays(Math.min(Math.max(days, 1), 30));
        if (exchange != null && !exchange.isBlank()) {
            return ResponseEntity.ok(
                    memberSnapshotRepository.findBySymbolAndExchangeAndMeasuredAtBetweenOrderByMeasuredAtAsc(symbol, exchange, from, to));
        }
        return ResponseEntity.ok(
                memberSnapshotRepository.findBySymbolAndMeasuredAtBetweenOrderByMeasuredAtAsc(symbol, from, to));
    }

    /** 수급 기준일 (데이터 없음이면 null). */
    @GetMapping("/flows/asof")
    public ResponseEntity<Map<String, Object>> flowsAsof() {
        Map<String, Object> m = new LinkedHashMap<>();
        Optional<LocalDate> max = flowRepository.findAll().stream()
                .map(WemadeInvestorFlow::getTradeDate).max(Comparator.naturalOrder());
        m.put("asOf", max.map(LocalDate::toString).orElse(null));
        m.put("hasData", max.isPresent());
        return ResponseEntity.ok(m);
    }

    /**
     * 인수계약 핵심 상수 (단일 출처).
     * 근거: DART rcpNo 20260630900888. 화면의 하드코딩을 이 API로 대체한다.
     */
    @GetMapping("/deal")
    public ResponseEntity<Map<String, Object>> deal() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("perShare", WemadeDealConstants.PER_SHARE_KRW);
        m.put("totalAmount", WemadeDealConstants.TOTAL_AMOUNT_KRW);
        m.put("stakePct", WemadeDealConstants.STAKE_PCT);
        m.put("stakeShares", WemadeDealConstants.STAKE_SHARES);
        m.put("deposit", WemadeDealConstants.DEPOSIT_KRW);
        m.put("balance", WemadeDealConstants.BALANCE_KRW);
        m.put("buyer", WemadeDealConstants.BUYER);
        m.put("rcpNo", WemadeDealConstants.RCP_NO);
        m.put("closeDate", WemadeDealConstants.CLOSE_DATE.toString());
        m.put("dday", ChronoUnit.DAYS.between(LocalDate.now(), WemadeDealConstants.CLOSE_DATE));
        List<Map<String, String>> timeline = new ArrayList<>();
        for (String[] s : WemadeDealConstants.TIMELINE) {
            Map<String, String> t = new LinkedHashMap<>();
            t.put("title", s[0]);
            t.put("sub", s[1]);
            t.put("status", s[2]);
            timeline.add(t);
        }
        m.put("timeline", timeline);
        return ResponseEntity.ok(m);
    }

    /**
     * 오늘의 위메이드 (티저용, 비회원 공개).
     * 규칙 기반 2~3줄 요약 — AI가 아니라 관측 데이터로만 구성한다.
     * 현재가(KIS 실시간 우선, 폴백 지연 시세) / 할인율 / D-day / 투자자 수급.
     */
    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> today(
            @RequestParam(defaultValue = "112040") String symbol) {
        Map<String, Object> m = new LinkedHashMap<>();

        Map<String, Object> quote = kisClient.realtimeQuote(symbol)
                .map(LinkedHashMap::new)
                .orElseGet(() -> new LinkedHashMap<>(priceService.overview(symbol)));
        long price = toLong(quote.get("price") != null ? quote.get("price") : quote.get("close"));
        double changeRate = toDouble(quote.get("changeRate"));
        long change = toLong(quote.get("change"));
        long volume = toLong(quote.get("volume"));
        boolean realtime = Boolean.TRUE.equals(quote.get("realtime"));
        m.put("price", price);
        m.put("change", change);
        m.put("changeRate", changeRate);
        m.put("volume", volume);
        m.put("realtime", realtime);
        m.put("badge", realtime ? "실시간" : "지연 시세");
        m.put("tradeDate", quote.get("tradeDate"));

        long perShare = WemadeDealConstants.PER_SHARE_KRW;
        double discountPct = price > 0 ? (perShare - price) * 100.0 / perShare : 0;
        long dday = ChronoUnit.DAYS.between(LocalDate.now(), WemadeDealConstants.CLOSE_DATE);
        m.put("perShare", perShare);
        m.put("discountPct", Math.round(discountPct * 10.0) / 10.0);
        m.put("multiple", price > 0 ? Math.round(perShare * 10.0 / price) / 10.0 : null);
        m.put("dday", dday);
        m.put("closeDate", WemadeDealConstants.CLOSE_DATE.toString());

        List<String> lines = new ArrayList<>();
        String dir = change > 0 ? "▲" : change < 0 ? "▼" : "–";
        lines.add(String.format("현재가 %,d원 (%s %,d원, %s%.2f%%) · 거래량 %,d주 · %s",
                price, dir, Math.abs(change), change >= 0 ? "+" : "", changeRate, volume,
                realtime ? "실시간" : "지연 시세"));
        if (dday >= 0) {
            lines.add(String.format("68,910원 인수계약 대비 %.1f%% 할인 · 잔금 D-%d (10/30 예정)",
                    discountPct, dday));
        } else {
            lines.add(String.format("68,910원 인수계약 대비 %.1f%% 할인 · 잔금일 경과", discountPct));
        }
        String flowLine = latestFlowLine();
        if (flowLine != null) lines.add(flowLine);
        m.put("lines", lines);
        return ResponseEntity.ok(m);
    }

    /** 최신 거래일 투자자 수급 한 줄 요약. 데이터 없으면 null. 금액 단위: 백만원 → 억원 변환. */
    private String latestFlowLine() {
        List<WemadeInvestorFlow> rows = flowRepository.findAll();
        if (rows.isEmpty()) return null;
        LocalDate max = rows.stream().map(WemadeInvestorFlow::getTradeDate)
                .max(Comparator.naturalOrder()).orElse(null);
        if (max == null) return null;
        Map<String, Long> net = new LinkedHashMap<>();
        for (WemadeInvestorFlow r : rows) {
            if (max.equals(r.getTradeDate()) && r.getNetBuyAmount() != null) {
                net.put(r.getInvestorType(), r.getNetBuyAmount());
            }
        }
        if (net.isEmpty()) return null;
        String[] names = {"FOREIGN", "INSTITUTION", "RETAIL"};
        String[] krs = {"외국인", "기관", "개인"};
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Long v = net.get(names[i]);
            if (v == null) continue;
            double eok = v / 100.0;
            parts.add(String.format("%s %s%.0f억원 %s", krs[i], v >= 0 ? "+" : "−",
                    Math.abs(eok), v >= 0 ? "순매수" : "순매도"));
        }
        if (parts.isEmpty()) return null;
        return String.join(" · ", parts) + " (" + max + " 기준)";
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o).replaceAll("[^0-9-]", "")); }
        catch (Exception e) { return 0; }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); }
        catch (Exception e) { return 0; }
    }

    /**
     * 당일 분봉 (1일 차트용). KIS FHKST03010200 페이지네이션.
     * 키가 없으면 빈 배열 — 호출자는 일봉으로 폴백한다.
     */
    @GetMapping("/prices/minute")
    public ResponseEntity<List<Map<String, Object>>> minutePrices(
            @RequestParam(defaultValue = "112040") String symbol) {
        return ResponseEntity.ok(kisClient.dayMinuteBars(symbol).orElse(List.of()));
    }
}
