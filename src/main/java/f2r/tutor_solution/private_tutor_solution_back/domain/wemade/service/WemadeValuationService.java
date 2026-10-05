package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeValuationComponent;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeValuationScenario;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeValuationComponentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeValuationScenarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 위메이드 SOTP 가치 재평가. 읽기 전용.
 * 합산은 DB 저장값의 deterministic 합계다. AI가 임의로 목표주가를 산출하지 않으며,
 * 단일 목표주가는 제공하지 않는다 (시나리오별 부분합 + 주당 환산만 제공).
 */
@Service
@RequiredArgsConstructor
public class WemadeValuationService {

    /** 위메이드 발행주식수(주). 출처: 리딩투자증권 위메이드 리포트 2025-12-09 (33,948천주). */
    public static final long SHARES_OUTSTANDING = 33_948_000L;
    /** SPA 1주당 가액(원). 출처: DART rcpNo 20260630900888 「1주당 가액(원) 68,910」 (A등급). */
    public static final long DEAL_PRICE_PER_SHARE = 68_910L;

    private final WemadeValuationComponentRepository componentRepository;
    private final WemadeValuationScenarioRepository scenarioRepository;
    private final WemadeRawPriceRepository priceRepository;

    public List<Map<String, Object>> components() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WemadeValuationComponent c : componentRepository.findAllByOrderByComponentKeyAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("componentKey", c.getComponentKey());
            m.put("nameKo", c.getNameKo());
            m.put("category", c.getCategory());
            m.put("valueLowKrw", c.getValueLowKrw());
            m.put("valueBaseKrw", c.getValueBaseKrw());
            m.put("valueHighKrw", c.getValueHighKrw());
            m.put("basis", c.getBasis());
            m.put("sourceName", c.getSourceName());
            m.put("sourceUrl", c.getSourceUrl());
            m.put("asOfDate", c.getAsOfDate() == null ? null : c.getAsOfDate().toString());
            m.put("grade", c.getGrade());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> scenarios() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WemadeValuationScenario s : scenarioRepository.findAllByOrderByScenarioKeyAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scenarioKey", s.getScenarioKey());
            m.put("nameKo", s.getNameKo());
            m.put("description", s.getDescription());
            m.put("assumptionsJson", s.getAssumptionsJson());
            out.add(m);
        }
        return out;
    }

    /**
     * 시나리오별 SOTP 부분합. NULL(미산정) 구성요소는 합산에서 제외하고 valued=false로 표시.
     */
    public Map<String, Object> sotp(String scenarioKey) {
        String key = scenarioKey == null ? "BASE"
                : scenarioKey.trim().toUpperCase(Locale.ROOT);
        WemadeValuationScenario scenario = scenarioRepository.findByScenarioKey(key)
                .orElseThrow(() -> new IllegalArgumentException("unknown scenario: " + scenarioKey));

        List<Map<String, Object>> parts = new ArrayList<>();
        long total = 0L;
        for (WemadeValuationComponent c : componentRepository.findAllByOrderByComponentKeyAsc()) {
            Long v = switch (key) {
                case "CONSERVATIVE" -> c.getValueLowKrw();
                case "OPTIMISTIC" -> c.getValueHighKrw();
                default -> c.getValueBaseKrw();
            };
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("componentKey", c.getComponentKey());
            m.put("nameKo", c.getNameKo());
            m.put("category", c.getCategory());
            m.put("valueKrw", v);
            m.put("valued", v != null);
            m.put("grade", c.getGrade());
            m.put("asOfDate", c.getAsOfDate() == null ? null : c.getAsOfDate().toString());
            if (v != null) total += v;
            parts.add(m);
        }

        // 현재 시가총액: DB(wemade_raw_prices) 최신 112040 종가 기준 deterministic 계산
        List<WemadeRawPrice> prices = priceRepository.findBySymbolOrderByTradeDateAsc("112040");
        WemadeRawPrice latest = prices.isEmpty() ? null : prices.get(prices.size() - 1);
        Integer closePrice = null;
        String priceDate = null;
        Long marketCap = null;
        if (latest != null && latest.getClosePrice() != null) {
            closePrice = latest.getClosePrice();
            priceDate = latest.getTradeDate().toString();
            marketCap = (long) closePrice * SHARES_OUTSTANDING;
        }

        Map<String, Object> marketCapInfo = new LinkedHashMap<>();
        marketCapInfo.put("closePriceKrw", closePrice);
        marketCapInfo.put("tradeDate", priceDate);
        marketCapInfo.put("sharesOutstanding", SHARES_OUTSTANDING);
        marketCapInfo.put("sharesOutstandingSource", "리딩투자증권 위메이드 리포트 2025-12-09 (33,948천주)");
        marketCapInfo.put("marketCapKrw", marketCap);

        // 거래 시사 가치 (참고용, SOTP 합산과 별개): 68,910원 × 발행주식수
        Map<String, Object> dealImplied = new LinkedHashMap<>();
        dealImplied.put("pricePerShareKrw", DEAL_PRICE_PER_SHARE);
        dealImplied.put("source", "DART rcpNo 20260630900888 「1주당 가액(원) 68,910」 (A등급)");
        dealImplied.put("impliedMarketCapKrw", DEAL_PRICE_PER_SHARE * SHARES_OUTSTANDING);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("scenarioKey", scenario.getScenarioKey());
        res.put("scenarioNameKo", scenario.getNameKo());
        res.put("description", scenario.getDescription());
        res.put("assumptionsJson", scenario.getAssumptionsJson());
        res.put("components", parts);
        res.put("totalKrw", total);
        res.put("perShareKrw", Math.round((double) total / SHARES_OUTSTANDING));
        res.put("sharesOutstanding", SHARES_OUTSTANDING);
        res.put("marketCap", marketCapInfo);
        res.put("dealImplied", dealImplied);
        res.put("notes", List.of(
                "부분 SOTP: 리서치로 확정된 구성요소만 합산.",
                "제외 항목: 위메이드플레이 지분(2026-04 위믹스코리아 38.28% 해소 후 위메이드 지분율 미확인), 순현금/부채(기준일 확정값 미확인), 핵심 게임 사업부 가치(별도 산정 없음).",
                "단일 목표주가를 제시하지 않음. 시나리오별 부분합과 주당 환산값은 가정 공개용 참고치.",
                "투자 권유 아님."
        ));
        return res;
    }
}
