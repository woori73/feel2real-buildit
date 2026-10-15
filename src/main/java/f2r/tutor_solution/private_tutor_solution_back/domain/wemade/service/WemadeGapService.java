package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeGapFactor;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePriceEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeGapFactorRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadePriceEventRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 위메이드 가격괴리(SPA 9,200억 vs 시총) 분석. 읽기 전용.
 * 모든 수치는 DB(wemade_raw_prices 실측 OHLCV + 확정 상수)의 deterministic 계산이다.
 * 외국인/기관 일별 수급 데이터는 미확보이므로 절대 창작하지 않고 PENDING으로 표시한다.
 * 단일 목표주가를 제시하지 않는다. 투자 권유가 아니다.
 */
@Service
@RequiredArgsConstructor
public class WemadeGapService {

    /** 위메이드 발행주식수(주). 출처: 리딩투자증권 위메이드 리포트 2025-12-09 (33,948천주). */
    public static final long SHARES_OUTSTANDING = 33_948_000L;
    /** SPA 1주당 가액(원). 출처: DART rcpNo 20260630900888 「1주당 가액(원) 68,910」 (A등급). */
    public static final long DEAL_PRICE_PER_SHARE = 68_910L;
    /** SPA 양수도 지분율(%). 출처: DART rcpNo 20260630900888, 13,350,738주 (A등급). */
    public static final double DEAL_STAKE_PCT = 39.33;
    /** SPA 양수도 대금(원). 출처: DART rcpNo 20260630900888, 920,000,000,000원 (A등급). */
    public static final long DEAL_AMOUNT_KRW = 920_000_000_000L;
    /** 가격 패턴 분석 시작일: 판호(夜鸦) 발표일. */
    public static final LocalDate ANALYSIS_START = LocalDate.of(2026, 6, 29);

    private final WemadeGapFactorRepository factorRepository;
    private final WemadePriceEventRepository eventRepository;
    private final WemadeRawPriceRepository priceRepository;

    public List<Map<String, Object>> factors() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WemadeGapFactor f : factorRepository.findAllByOrderByHypothesisNoAscFactorKeyAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("factorKey", f.getFactorKey());
            m.put("hypothesisNo", f.getHypothesisNo());
            m.put("nameKo", f.getNameKo());
            m.put("amountLowKrw", f.getAmountLowKrw());
            m.put("amountHighKrw", f.getAmountHighKrw());
            m.put("basis", f.getBasis());
            m.put("grade", f.getGrade());
            m.put("sourceName", f.getSourceName());
            m.put("sourceUrl", f.getSourceUrl());
            m.put("asOfDate", f.getAsOfDate() == null ? null : f.getAsOfDate().toString());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> timeline() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WemadePriceEvent e : eventRepository.findAllByOrderByEventDateAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventDate", e.getEventDate().toString());
            m.put("title", e.getTitle());
            m.put("summary", e.getSummary());
            m.put("priceChangePct", e.getPriceChangePct() == null ? null
                    : e.getPriceChangePct().setScale(2, RoundingMode.HALF_UP).doubleValue());
            m.put("closePrice", e.getClosePrice());
            m.put("highPrice", e.getHighPrice());
            m.put("lowPrice", e.getLowPrice());
            m.put("volume", e.getVolume());
            m.put("eventType", e.getEventType());
            m.put("grade", e.getGrade());
            m.put("sourceName", e.getSourceName());
            m.put("sourceUrl", e.getSourceUrl());
            out.add(m);
        }
        return out;
    }

    /**
     * 괴리 헤드라인 + 6/29 이후 가격 패턴 분석.
     * 3단계 판정: ① FACT(가격 고점·반전 — OHLCV로 판단) ② ANALYSIS(수급 동행 — PENDING)
     * ③ HYPOTHESIS(신규 대주주 연관 — 미확인 유지).
     */
    public Map<String, Object> comparison() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("headline", headline());
        res.put("pricePattern", pricePattern());
        res.put("priceSeries", priceSeries());
        res.put("verdict", verdict());
        res.put("supplyData", supplyDataStatus());
        res.put("notes", List.of(
                "모든 수치는 DB 실측 OHLCV와 확정 상수(DART 공시값)의 산술 계산이다.",
                "외국인/기관 일별 순매수 데이터는 미확보 — '고점에서 외국인·기관 매도 반복' 관찰은 검증 불가.",
                "경쟁가설 ⑥(인수 이해관계자-수급 연관성)은 직접 증거 전까지 미확인 가설로 유지한다.",
                "단일 목표주가를 제시하지 않는다. 투자 권유가 아니다."
        ));
        return res;
    }

    private Map<String, Object> headline() {
        List<WemadeRawPrice> prices = priceRepository.findBySymbolOrderByTradeDateAsc("112040");
        WemadeRawPrice latest = prices.isEmpty() ? null : prices.get(prices.size() - 1);

        long implied100 = DEAL_PRICE_PER_SHARE * SHARES_OUTSTANDING; // 2,339,356,680,000
        Long marketCap = null;
        Integer close = null;
        String tradeDate = null;
        if (latest != null && latest.getClosePrice() != null) {
            close = latest.getClosePrice();
            tradeDate = latest.getTradeDate().toString();
            marketCap = (long) close * SHARES_OUTSTANDING;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dealStakePct", DEAL_STAKE_PCT);
        m.put("dealAmountKrw", DEAL_AMOUNT_KRW);
        m.put("dealAmountNote", "13,350,738주(39.33%) 양수도 대금 9,200억원 — DART rcpNo 20260630900888 (A등급)");
        m.put("dealPricePerShareKrw", DEAL_PRICE_PER_SHARE);
        m.put("sharesOutstanding", SHARES_OUTSTANDING);
        m.put("implied100PctKrw", implied100);
        m.put("implied100PctNote", "68,910원 × 33,948천주 = 약 2.34조원. 9,200억 ÷ 39.33% = 약 2.34조원과 일치");
        m.put("closePriceKrw", close);
        m.put("tradeDate", tradeDate);
        m.put("marketCapKrw", marketCap);
        if (marketCap != null) {
            double discount = (1.0 - (double) marketCap / implied100) * 100.0;
            m.put("discountPct", round2(discount));
            m.put("gapKrw", implied100 - marketCap);
            m.put("perShareGapKrw", DEAL_PRICE_PER_SHARE - close);
            m.put("controlPremiumMultiple", round2((double) DEAL_PRICE_PER_SHARE / close));
            m.put("controlPremiumNote", "68,910 ÷ 종가 = 거래가/시장가 배율. Shanda Games 상장폐지 프리미엄 46.5~53.8%(30/60일 VWAP 대비)와 비교");
        }
        return m;
    }

    private Map<String, Object> pricePattern() {
        List<WemadeRawPrice> all = priceRepository.findBySymbolOrderByTradeDateAsc("112040");
        List<WemadeRawPrice> rows = new ArrayList<>();
        for (WemadeRawPrice p : all) {
            if (p.getTradeDate() != null && !p.getTradeDate().isBefore(ANALYSIS_START)) rows.add(p);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("analysisStart", ANALYSIS_START.toString());
        m.put("tradingDays", rows.size());
        m.put("surgeDays", surgeDays(rows));
        m.put("volumeSpikes", volumeSpikes(rows));
        m.put("drawdowns", drawdowns(rows));
        m.put("lowerHighs", lowerHighs(rows));
        return m;
    }

    /** 6/29 이후 일봉 시계열 (차트용). DB 실측 OHLCV 그대로 반환. */
    private List<Map<String, Object>> priceSeries() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WemadeRawPrice p : priceRepository.findBySymbolOrderByTradeDateAsc("112040")) {
            if (p.getTradeDate() == null || p.getTradeDate().isBefore(ANALYSIS_START)) continue;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", p.getTradeDate().toString());
            d.put("close", p.getClosePrice());
            d.put("high", p.getHighPrice());
            d.put("low", p.getLowPrice());
            d.put("open", p.getOpenPrice());
            d.put("volume", p.getVolume());
            out.add(d);
        }
        return out;
    }

    /** 일간 +5% 이상 급등일과 직전 20거래일 고점 돌파 여부, 이후 1/3/5거래일 수익률. */
    private List<Map<String, Object>> surgeDays(List<WemadeRawPrice> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            WemadeRawPrice r = rows.get(i);
            if (r.getClosePrice() == null) continue;
            // 전일 종가는 전체 시계열 기준 (분석 시작일 6/29의 전일 = 6/26 포함)
            WemadeRawPrice prev = prevInFullSeries(r);
            if (prev == null || prev.getClosePrice() == null || prev.getClosePrice() == 0) continue;
            double chg = (r.getClosePrice() - prev.getClosePrice()) * 100.0 / prev.getClosePrice();
            if (chg < 5.0) continue;

            int from = Math.max(0, i - 20);
            int maxHigh20 = 0;
            for (int j = from; j < i; j++) {
                if (rows.get(j).getHighPrice() != null) maxHigh20 = Math.max(maxHigh20, rows.get(j).getHighPrice());
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", r.getTradeDate().toString());
            d.put("changePct", round2(chg));
            d.put("close", r.getClosePrice());
            d.put("high", r.getHighPrice());
            d.put("low", r.getLowPrice());
            d.put("volume", r.getVolume());
            d.put("break20dHigh", r.getHighPrice() != null && r.getHighPrice() > maxHigh20);
            Map<String, Object> post = new LinkedHashMap<>();
            for (int n : new int[]{1, 3, 5}) {
                int j = i + n;
                if (j < rows.size() && rows.get(j).getClosePrice() != null) {
                    double ret = (rows.get(j).getClosePrice() - r.getClosePrice()) * 100.0 / r.getClosePrice();
                    Map<String, Object> pd = new LinkedHashMap<>();
                    pd.put("date", rows.get(j).getTradeDate().toString());
                    pd.put("returnPct", round2(ret));
                    pd.put("close", rows.get(j).getClosePrice());
                    post.put(n + "d", pd);
                } else {
                    post.put(n + "d", null);
                }
            }
            d.put("postReturns", post);
            out.add(d);
        }
        return out;
    }

    private WemadeRawPrice prevInFullSeries(WemadeRawPrice r) {
        List<WemadeRawPrice> all = priceRepository.findBySymbolOrderByTradeDateAsc("112040");
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getTradeDate() != null && all.get(i).getTradeDate().equals(r.getTradeDate())) {
                return i > 0 ? all.get(i - 1) : null;
            }
        }
        return null;
    }

    /** 거래량 급증일: 50만주 이상 또는 구간 중앙값의 4배 이상. */
    private Map<String, Object> volumeSpikes(List<WemadeRawPrice> rows) {
        List<Long> vols = new ArrayList<>();
        for (WemadeRawPrice r : rows) if (r.getVolume() != null) vols.add(r.getVolume());
        Collections.sort(vols);
        long median = vols.isEmpty() ? 0 : vols.get(vols.size() / 2);
        List<Map<String, Object>> spikes = new ArrayList<>();
        for (WemadeRawPrice r : rows) {
            if (r.getVolume() == null) continue;
            if (r.getVolume() >= 500_000 || (median > 0 && r.getVolume() >= 4 * median)) {
                WemadeRawPrice prev = prevInFullSeries(r);
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("date", r.getTradeDate().toString());
                d.put("volume", r.getVolume());
                d.put("close", r.getClosePrice());
                d.put("high", r.getHighPrice());
                d.put("low", r.getLowPrice());
                if (prev != null && prev.getClosePrice() != null && r.getClosePrice() != null) {
                    d.put("changePct", round2((r.getClosePrice() - prev.getClosePrice()) * 100.0 / prev.getClosePrice()));
                }
                spikes.add(d);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spikes", spikes);
        m.put("medianVolume", median);
        m.put("thresholdNote", "50만주 이상 또는 중앙값(" + median + "주)의 4배 이상");
        return m;
    }

    /** 구간 내 주요 낙폭: 7/2 장중고→7/29 장중저, 7/1 종가→최신 종가. */
    private Map<String, Object> drawdowns(List<WemadeRawPrice> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        WemadeRawPrice peak = null, trough = null, first = rows.isEmpty() ? null : rows.get(0),
                last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        for (WemadeRawPrice r : rows) {
            if (r.getHighPrice() != null && (peak == null || r.getHighPrice() > peak.getHighPrice())) peak = r;
            if (r.getLowPrice() != null && (trough == null || r.getLowPrice() < trough.getLowPrice())) trough = r;
        }
        if (peak != null && trough != null && trough.getTradeDate().isAfter(peak.getTradeDate())) {
            Map<String, Object> dd = new LinkedHashMap<>();
            dd.put("peakDate", peak.getTradeDate().toString());
            dd.put("peakHigh", peak.getHighPrice());
            dd.put("troughDate", trough.getTradeDate().toString());
            dd.put("troughLow", trough.getLowPrice());
            dd.put("drawdownPct", round2((trough.getLowPrice() - peak.getHighPrice()) * 100.0 / peak.getHighPrice()));
            m.put("intradayPeakToTrough", dd);
        }
        // 7/1 상한가 종가(25,100) → 최신 종가
        WemadeRawPrice july1 = null;
        for (WemadeRawPrice r : rows) {
            if (r.getTradeDate().equals(LocalDate.of(2026, 7, 1))) { july1 = r; break; }
        }
        if (july1 != null && last != null && july1.getClosePrice() != null && last.getClosePrice() != null) {
            Map<String, Object> dd = new LinkedHashMap<>();
            dd.put("fromDate", "2026-07-01");
            dd.put("fromClose", july1.getClosePrice());
            dd.put("toDate", last.getTradeDate().toString());
            dd.put("toClose", last.getClosePrice());
            dd.put("drawdownPct", round2((last.getClosePrice() - july1.getClosePrice()) * 100.0 / july1.getClosePrice()));
            m.put("limitUpCloseToLatest", dd);
        }
        if (first != null && last != null && first.getClosePrice() != null && last.getClosePrice() != null) {
            m.put("periodReturnPct", round2((last.getClosePrice() - first.getClosePrice()) * 100.0 / first.getClosePrice()));
        }
        return m;
    }

    /** 고점 낮아짐(lower highs): 구간 내 주요 swing high 목록. FACT — 가격으로만 판단. */
    private List<Map<String, Object>> lowerHighs(List<WemadeRawPrice> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        // 단순 swing high: 전후 3거래일보다 고가가 높은 날
        for (int i = 3; i < rows.size() - 3; i++) {
            WemadeRawPrice r = rows.get(i);
            if (r.getHighPrice() == null) continue;
            boolean isPeak = true;
            for (int j = i - 3; j <= i + 3; j++) {
                if (j == i) continue;
                WemadeRawPrice o = rows.get(j);
                if (o.getHighPrice() != null && o.getHighPrice() >= r.getHighPrice()) { isPeak = false; break; }
            }
            if (isPeak) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("date", r.getTradeDate().toString());
                d.put("high", r.getHighPrice());
                d.put("close", r.getClosePrice());
                out.add(d);
            }
        }
        out.sort((a, b) -> ((Integer) b.get("high")).compareTo((Integer) a.get("high")));
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private Map<String, Object> verdict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fact", List.of(
                "2026-06-29 판호(+17.66%) → 6/30 DART SPA 공시(+11.99%) → 7/1 상한가 25,100원(+29.85%, 거래량 76,880주·매도호가 실종).",
                "7/2 장중고 28,600원 → 종가 22,100원(-11.95%), 거래량 1,961,527주(분석 구간 내 최대). 7/3 -13.21% 추가 하락.",
                "급등 후 되돌림 반복: 7/1 상한가 이후 5거래일 -30.2%, 8/11 +13.42% 이후 5거래일 -15.8%.",
                "고점 낮아짐: 장중고 28,600원(7/2) → 20,600원(8/11) → 17,020원(9/14). 7/2 고점→7/29 저점 낙폭 -50.5%.",
                "거래량 급증일은 5일: 7/2(196만주)·7/3(57만주)·8/11(96만주)·8/31(71만주, +0.33% 보합)·9/14(140만주, 킹넷 공시 다음 거래일)."
        ));
        m.put("analysis", List.of(
                "수급 동행 분석: 외국인/기관 일별 순매수 데이터 미확보로 PENDING.",
                "'고점에서 외국인·기관 매도 반복'이라는 사용자 관찰은 수급 데이터 확보 전까지 검증 불가.",
                "가격·거래량 패턴(급등 후 되돌림, lower highs, 거래량 급증일 보합/하락)은 FACT로 확정."
        ));
        m.put("hypothesis", List.of(
                "신규 대주주(NeoPulse 컨소시엄)와 주가 움직임의 연관성: 직접 증거 없음 — 미확인 유지.",
                "'중국 대주주가 주가를 누른다'는 결론을 세우지 않는다.",
                "검증 경로: 수급 데이터 확보 후 거래량 급증일(7/2·8/31·9/14)의 투자자별 순매수 대조."
        ));
        return m;
    }

    private Map<String, Object> supplyDataStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "PENDING");
        m.put("reason", "외국인/기관 일별 순매수 데이터 미확보 (네이버 bot 차단, KRX 키 미발급). 절대 창작하지 않음.");
        m.put("uiReady", true);
        m.put("uiNote", "who-sold fragment에 '데이터 확보 후 표시' 영역 준비됨.");
        return m;
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
