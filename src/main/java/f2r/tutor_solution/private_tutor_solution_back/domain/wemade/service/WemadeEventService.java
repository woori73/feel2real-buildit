package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto.WemadeEventView;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeEventRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeRawPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 이벤트 + 주가 결합. 이벤트일(휴장이면 다음 거래일) 종가를 기준으로
 * 이후 1/3/5/20거래일 변화율을 측정한다. 미래 예측이 아니다.
 */
@Service
@RequiredArgsConstructor
public class WemadeEventService {

    private final WemadeEventRepository eventRepository;
    private final WemadeRawPriceRepository priceRepository;

    @Transactional(readOnly = true)
    public List<WemadeEventView> eventsWithPriceReaction() {
        List<WemadeEvent> events = eventRepository.findAllByOrderByEventDateDesc();
        // 이벤트 주가 결합은 위메이드(112040) 기준
        List<WemadeRawPrice> prices = priceRepository.findBySymbolOrderByTradeDateAsc("112040");
        NavigableMap<LocalDate, WemadeRawPrice> byDate = new TreeMap<>();
        List<LocalDate> dates = new ArrayList<>();
        for (WemadeRawPrice p : prices) { byDate.put(p.getTradeDate(), p); dates.add(p.getTradeDate()); }

        List<WemadeEventView> out = new ArrayList<>();
        for (WemadeEvent e : events) {
            LocalDate base = byDate.ceilingKey(e.getEventDate()); // 휴장이면 다음 거래일
            Integer priceAt = null;
            Double c1 = null, c3 = null, c5 = null, c20 = null;
            if (base != null) {
                int idx = dates.indexOf(base);
                priceAt = byDate.get(base).getClosePrice();
                c1 = pct(priceAt, closeAt(dates, byDate, idx, 1));
                c3 = pct(priceAt, closeAt(dates, byDate, idx, 3));
                c5 = pct(priceAt, closeAt(dates, byDate, idx, 5));
                c20 = pct(priceAt, closeAt(dates, byDate, idx, 20));
            }
            out.add(new WemadeEventView(
                    e.getId(), e.getEventDate(), e.getCategory(), e.getTitle(), e.getSummary(),
                    e.getSource(), e.getSourceName(), e.getSourceUrl(), e.getFactGrade(),
                    priceAt, base, c1, c3, c5, c20));
        }
        return out;
    }

    private Integer closeAt(List<LocalDate> dates, NavigableMap<LocalDate, WemadeRawPrice> byDate, int idx, int plus) {
        int j = idx + plus;
        if (j >= dates.size()) return null;
        return byDate.get(dates.get(j)).getClosePrice();
    }

    private Double pct(int base, Integer future) {
        if (future == null || base == 0) return null;
        return Math.round((future - base) * 10000.0 / base) / 100.0;
    }
}
