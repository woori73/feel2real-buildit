package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto.WemadeEventView;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto.WemadePricePoint;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeChinaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeEventService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadePriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
}
