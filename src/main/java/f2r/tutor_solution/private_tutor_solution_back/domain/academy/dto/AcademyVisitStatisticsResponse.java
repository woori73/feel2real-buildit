package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.time.LocalDate;
import java.util.List;

public record AcademyVisitStatisticsResponse(
    long today,
    long total,
    List<DailyVisit> recentDays
) {
  public record DailyVisit(LocalDate date, long count) {
  }
}

