package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto;

import java.time.LocalDate;

/** 차트용 일봉 + 지표 포인트. 지표는 서버에서 계산된 값이다. */
public record WemadePricePoint(
        LocalDate date,
        Integer open,
        Integer high,
        Integer low,
        Integer close,
        Long volume,
        Double ma5,
        Double ma20,
        Double ma60,
        Double ma120,
        Double rsi14,
        Double macd,
        Double signal,
        Double high52w,
        Double low52w
) {}
