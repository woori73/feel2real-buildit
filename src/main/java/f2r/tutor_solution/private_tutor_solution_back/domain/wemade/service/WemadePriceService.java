package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto.WemadePricePoint;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 주가 조회 서비스. 지표는 WemadeIndicatorService로 deterministic 계산한다.
 */
@Service
@RequiredArgsConstructor
public class WemadePriceService {

    private final WemadeRawPriceRepository priceRepository;

    /** 최근 N거래일 일봉 + 지표. N은 최대 전체 범위. */
    @Transactional(readOnly = true)
    public List<WemadePricePoint> recentWithIndicators(int days) {
        List<WemadeRawPrice> all = priceRepository.findAllByOrderByTradeDateAsc();
        if (all.isEmpty()) return List.of();

        // 지표는 전체 히스토리 기준으로 계산해야 warm-up이 올바르다.
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

    /** 개요: 최근가/등락/52주. 데이터 없으면 null 필드로 반환. */
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<WemadeRawPrice> all = priceRepository.findAllByOrderByTradeDateAsc();
        Map<String, Object> m = new TreeMap<>();
        m.put("stockCode", "112040");
        m.put("name", "위메이드");
        if (all.isEmpty()) {
            m.put("hasData", false);
            return m;
        }
        WemadeRawPrice last = all.get(all.size() - 1);
        WemadeRawPrice prev = all.size() > 1 ? all.get(all.size() - 2) : null;
        m.put("hasData", true);
        m.put("tradeDate", last.getTradeDate().toString());
        m.put("close", last.getClosePrice());
        m.put("open", last.getOpenPrice());
        m.put("high", last.getHighPrice());
        m.put("low", last.getLowPrice());
        m.put("volume", last.getVolume());
        if (prev != null) {
            m.put("change", last.getClosePrice() - prev.getClosePrice());
            m.put("changeRate", round2((last.getClosePrice() - prev.getClosePrice()) * 100.0 / prev.getClosePrice()));
        }
        List<Double> highs = all.stream().map(p -> (double) p.getHighPrice()).toList();
        List<Double> lows = all.stream().map(p -> (double) p.getLowPrice()).toList();
        m.put("high52w", WemadeIndicatorService.high52w(highs).intValue());
        m.put("low52w", WemadeIndicatorService.low52w(lows).intValue());
        return m;
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
