package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AcademyAccessStatisticsResponse(
    String metric,
    long total,
    List<Daily> daily,
    Instant lastAggregatedAt) {
  public record Daily(LocalDate date, long count) {}
}
