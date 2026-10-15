package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.dto;

import java.time.LocalDate;

/** 이벤트 + 당시 주가 + 이후 N거래일 변화율. 미래 예측이 아니라 과거 반응 측정이다. */
public record WemadeEventView(
        Long id,
        LocalDate eventDate,
        String category,
        String title,
        String summary,
        String source,
        String sourceName,
        String sourceUrl,
        String factGrade,
        Integer priceAtEvent,
        LocalDate priceDate,
        Double change1d,
        Double change3d,
        Double change5d,
        Double change20d
) {}
