package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeInvestorFlow;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeSurgeAnalysis;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeInvestorFlowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeSurgeAnalysisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 급등일 수급 분석 서비스.
 *
 * - 급등일(당일 등락률 +5%/+10%/+20% 이상, 또는 직전 고점 돌파)을 탐지한다.
 * - 각 급등일의 외국인·기관·개인 순매수 금액과 이후 1/3/5/10거래일 수익률을 계산해
 *   wemade_surge_analysis에 저장한다.
 * - 수급 데이터가 PENDING(미확보)인 동안은 수급 필드를 null로 두고도 행을 만들지 않는다.
 *   (급등일 탐지 자체는 OHLCV만으로 가능하므로, 수급 확보 후 재계산한다.)
 * - '고점 외국인·기관 매도 반복' 가설 검증의 기초 데이터로 사용한다.
 *   결론을 먼저 내리지 않고 FACT(데이터)만 쌓는다.
 */
@Slf4j
@Service
public class WemadeSurgeAnalysisService {

    private final WemadeRawPriceRepository priceRepository;
    private final WemadeInvestorFlowRepository flowRepository;
    private final WemadeSurgeAnalysisRepository surgeRepository;

    public WemadeSurgeAnalysisService(WemadeRawPriceRepository priceRepository,
                                     WemadeInvestorFlowRepository flowRepository,
                                     WemadeSurgeAnalysisRepository surgeRepository) {
        this.priceRepository = priceRepository;
        this.flowRepository = flowRepository;
        this.surgeRepository = surgeRepository;
    }

    /**
     * 지정 종목의 급등일을 분석해 저장한다. 수급 미확보 시 수급 필드는 null.
     * @return 신규 저장된 행 수
     */
    @Transactional
    public int analyzeSurges(String symbol) {
        List<WemadeRawPrice> prices =
                priceRepository.findBySymbolOrderByTradeDateAsc(symbol);
        if (prices.size() < 12) {
            log.info("WEMADE surge analysis skipped for {}: only {} price rows", symbol, prices.size());
            return 0;
        }
        // 일별 수급 맵 (없으면 빈 맵)
        Map<LocalDate, Map<String, Long>> flows = loadFlows();
        boolean hasFlows = !flows.isEmpty();

        int saved = 0;
        double prevHigh = 0;
        for (int i = 1; i < prices.size(); i++) {
            WemadeRawPrice prev = prices.get(i - 1);
            WemadeRawPrice cur = prices.get(i);
            if (prev.getClosePrice() == null || prev.getClosePrice() == 0
                    || cur.getClosePrice() == null) continue;
            double chgRate = (cur.getClosePrice() - prev.getClosePrice()) * 100.0
                    / prev.getClosePrice();
            String surgeType = null;
            if (chgRate >= 20) surgeType = "SURGE_20";
            else if (chgRate >= 10) surgeType = "SURGE_10";
            else if (chgRate >= 5) surgeType = "SURGE_5";
            else if (cur.getHighPrice() != null && cur.getHighPrice() > prevHigh
                    && prevHigh > 0) surgeType = "BREAKOUT_HIGH";
            if (cur.getHighPrice() != null) prevHigh = Math.max(prevHigh, cur.getHighPrice());
            if (surgeType == null) continue;

            String hash = WemadeCollectionService.sha256(
                    "SURGE|" + symbol + "|" + cur.getTradeDate() + "|" + surgeType);
            if (surgeRepository.existsByContentHash(hash)) continue;

            WemadeSurgeAnalysis row = WemadeSurgeAnalysis.builder()
                    .symbol(symbol)
                    .surgeDate(cur.getTradeDate())
                    .surgeType(surgeType)
                    .closePrice(cur.getClosePrice())
                    .changeRate(round2(chgRate))
                    .ret1d(fwdReturn(prices, i, 1))
                    .ret3d(fwdReturn(prices, i, 3))
                    .ret5d(fwdReturn(prices, i, 5))
                    .ret10d(fwdReturn(prices, i, 10))
                    .contentHash(hash)
                    .analyzedAt(LocalDateTime.now())
                    .build();
            Map<String, Long> f = flows.get(cur.getTradeDate());
            if (f != null) {
                row.setForeignNetBuy(f.get("FOREIGN"));
                row.setInstitutionNetBuy(f.get("INSTITUTION"));
                row.setRetailNetBuy(f.get("RETAIL"));
                row.setFlowSource("PYKRX");
            } else if (hasFlows) {
                row.setFlowSource("PYKRX");
            }
            // 수급 미확보 시 flow 필드는 null로 둔다 (0으로 표시하지 않는다).
            surgeRepository.save(row);
            saved++;
        }
        log.info("WEMADE surge analysis for {}: {} new rows (flowsAvailable={})",
                symbol, saved, hasFlows);
        return saved;
    }

    /** 급등일 목록 조회. */
    @Transactional(readOnly = true)
    public List<WemadeSurgeAnalysis> list(String symbol) {
        return surgeRepository.findBySymbolOrderBySurgeDateDesc(symbol);
    }

    /** '고점 외국인·기관 매도 반복' 가설 검증용 집계. 수급 없으면 빈 결과. */
    @Transactional(readOnly = true)
    public Map<String, Object> hypothesisSummary(String symbol) {
        List<WemadeSurgeAnalysis> rows =
                surgeRepository.findBySymbolOrderBySurgeDateDesc(symbol);
        Map<String, Object> m = new HashMap<>();
        m.put("surgeDays", rows.size());
        long withFlows = rows.stream().filter(r -> r.getForeignNetBuy() != null).count();
        m.put("daysWithFlowData", withFlows);
        m.put("verifiable", withFlows > 0);
        if (withFlows == 0) {
            m.put("note", "수급 데이터 미확보로 '고점 외국인·기관 매도 반복' 가설은 검증 불가. "
                    + "급등일 목록만 제공하며 결론을 내리지 않는다.");
        }
        return m;
    }

    private Map<LocalDate, Map<String, Long>> loadFlows() {
        Map<LocalDate, Map<String, Long>> out = new HashMap<>();
        for (WemadeInvestorFlow f : flowRepository.findAll()) {
            out.computeIfAbsent(f.getTradeDate(), d -> new HashMap<>())
                    .put(f.getInvestorType(), f.getNetBuyAmount());
        }
        return out;
    }

    private Double fwdReturn(List<WemadeRawPrice> prices, int idx, int days) {
        if (idx + days >= prices.size()) return null;
        Integer base = prices.get(idx).getClosePrice();
        Integer fwd = prices.get(idx + days).getClosePrice();
        if (base == null || base == 0 || fwd == null) return null;
        return round2((fwd - base) * 100.0 / base);
    }

    private static Double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
