package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto.WemadePricePoint;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주가 조회 서비스. 지표는 WemadeIndicatorService로 deterministic 계산한다.
 * 다종목 대응: symbol 파라미터 (112040/101730/002517).
 * 통화: 112040·101730=KRW, 002517=CNY(100배 스케일 저장) — 비교 시 정규화 수익률 사용.
 */
@Service
@RequiredArgsConstructor
public class WemadePriceService {

    /** 종목 메타. 순서대로: 위메이드, 위메이드맥스, 킹넷. */
    public static final List<Map<String, String>> SYMBOLS = List.of(
            Map.of("symbol", "112040", "name", "위메이드", "market", "KOSDAQ", "currency", "KRW"),
            Map.of("symbol", "101730", "name", "위메이드맥스", "market", "KOSDAQ", "currency", "KRW"),
            Map.of("symbol", "002517", "name", "킹넷(恺英网络)", "market", "SZSE", "currency", "CNY")
    );

    private final WemadeRawPriceRepository priceRepository;

    public static Map<String, String> symbolMeta(String symbol) {
        return SYMBOLS.stream().filter(s -> s.get("symbol").equals(symbol))
                .findFirst().orElse(Map.of("symbol", symbol, "name", symbol, "market", "", "currency", ""));
    }

    /** 002517(CNY, 100배 스케일)을 표시 단위로 변환. */
    public static double displayPrice(String symbol, int stored) {
        return "002517".equals(symbol) ? stored / 100.0 : stored;
    }

    /** 최근 N거래일 일봉 + 지표. N은 최대 전체 범위. */
    @Transactional(readOnly = true)
    public List<WemadePricePoint> recentWithIndicators(String symbol, int days) {
        List<WemadeRawPrice> all = priceRepository.findBySymbolOrderByTradeDateAsc(symbol);
        if (all.isEmpty()) return List.of();

        List<Double> closes = all.stream().map(p -> (double) p.getClosePrice()).toList();
        List<Double> highs = all.stream().map(p -> (double) p.getHighPrice()).toList();
        List<Double> lows = all.stream().map(p -> (double) p.getLowPrice()).toList();
        List<Double> rsi = WemadeIndicatorService.rsi14(closes);
        List<Double> macd = WemadeIndicatorService.macdLine(closes);
        List<Double> signal = WemadeIndicatorService.macdSignal(macd);

        int from = Math.max(0, all.size() - Math.max(1, days));
        List<WemadePricePoint> out = new ArrayList<>();
        for (int i = from; i < all.size(); i++) {
            WemadeRawPrice p = all.get(i);
            out.add(new WemadePricePoint(
                    p.getTradeDate(), p.getOpenPrice(), p.getHighPrice(), p.getLowPrice(),
                    p.getClosePrice(), p.getVolume(),
                    WemadeIndicatorService.sma(closes, 5, i),
                    WemadeIndicatorService.sma(closes, 20, i),
                    WemadeIndicatorService.sma(closes, 60, i),
                    WemadeIndicatorService.sma(closes, 120, i),
                    rsi.get(i), macd.get(i), signal.get(i),
                    WemadeIndicatorService.high52w(highs.subList(0, i + 1)),
                    WemadeIndicatorService.low52w(lows.subList(0, i + 1))
            ));
        }
        return out;
    }

    /** 개요: 최근가/등락/52주. 데이터 없으면 hasData=false. */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(String symbol) {
        List<WemadeRawPrice> all = priceRepository.findBySymbolOrderByTradeDateAsc(symbol);
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> meta = symbolMeta(symbol);
        m.put("symbol", symbol);
        m.put("name", meta.get("name"));
        m.put("market", meta.get("market"));
        m.put("currency", meta.get("currency"));
        if (all.isEmpty()) {
            m.put("hasData", false);
            return m;
        }
        WemadeRawPrice last = all.get(all.size() - 1);
        WemadeRawPrice prev = all.size() > 1 ? all.get(all.size() - 2) : null;
        m.put("hasData", true);
        m.put("tradeDate", last.getTradeDate().toString());
        m.put("close", displayPrice(symbol, last.getClosePrice()));
        m.put("open", displayPrice(symbol, last.getOpenPrice()));
        m.put("high", displayPrice(symbol, last.getHighPrice()));
        m.put("low", displayPrice(symbol, last.getLowPrice()));
        m.put("volume", last.getVolume());
        if (prev != null) {
            double chg = displayPrice(symbol, last.getClosePrice()) - displayPrice(symbol, prev.getClosePrice());
            m.put("change", round2(chg));
            m.put("changeRate", round2(chg * 100.0 / displayPrice(symbol, prev.getClosePrice())));
        }
        List<Double> highs = all.stream().map(p -> (double) p.getClosePrice()).toList();
        List<Double> lows = all.stream().map(p -> (double) p.getClosePrice()).toList();
        // 52주는 고가/저가 기준 (표시는 통화 단위)
        List<Double> h = all.stream().map(p -> (double) p.getHighPrice()).toList();
        List<Double> l = all.stream().map(p -> (double) p.getLowPrice()).toList();
        m.put("high52w", round2(displayPrice(symbol, WemadeIndicatorService.high52w(h).intValue())));
        m.put("low52w", round2(displayPrice(symbol, WemadeIndicatorService.low52w(l).intValue())));
        return m;
    }

    /** 정규화 수익률 비교. base 날짜(없으면 가장 가까운 이전 거래일) 종가 = 100. */
    @Transactional(readOnly = true)
    public Map<String, Object> compareNormalized(List<String> symbols, String baseDateStr, int days) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseDate", baseDateStr);
        out.put("note", "통화 차이(KRW/CNY)와 거래시간대 차이를 제거하기 위한 정규화 수익률. 절대가격 비교가 아님.");
        List<Map<String, Object>> series = new ArrayList<>();
        java.time.LocalDate baseDate = java.time.LocalDate.parse(baseDateStr);
        for (String symbol : symbols) {
            List<WemadeRawPrice> all = priceRepository.findBySymbolOrderByTradeDateAsc(symbol);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("symbol", symbol);
            s.put("name", symbolMeta(symbol).get("name"));
            s.put("currency", symbolMeta(symbol).get("currency"));
            if (all.isEmpty()) {
                s.put("hasData", false);
                series.add(s);
                continue;
            }
            // base일 또는 가장 가까운 이전 거래일
            WemadeRawPrice base = null;
            for (int i = all.size() - 1; i >= 0; i--) {
                if (!all.get(i).getTradeDate().isAfter(baseDate)) { base = all.get(i); break; }
            }
            if (base == null) base = all.get(0);
            double baseClose = base.getClosePrice();
            java.time.LocalDate from = base.getTradeDate();
            List<Map<String, Object>> points = new ArrayList<>();
            int count = 0;
            for (WemadeRawPrice p : all) {
                if (p.getTradeDate().isBefore(from)) continue;
                if (count >= days) break;
                Map<String, Object> pt = new LinkedHashMap<>();
                pt.put("date", p.getTradeDate().toString());
                pt.put("value", round2(p.getClosePrice() * 100.0 / baseClose));
                points.add(pt);
                count++;
            }
            s.put("hasData", true);
            s.put("basePrice", displayPrice(symbol, (int) baseClose));
            s.put("baseTradeDate", base.getTradeDate().toString());
            s.put("points", points);
            series.add(s);
        }
        out.put("series", series);
        return out;
    }

    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
