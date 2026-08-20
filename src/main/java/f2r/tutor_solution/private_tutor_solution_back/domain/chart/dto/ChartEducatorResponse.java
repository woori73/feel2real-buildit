package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartEducator;
import java.time.LocalDateTime;
import java.util.List;

public record ChartEducatorResponse(
    int rank,
    Long educatorId,
    String name,
    String role,
    String academyName,
    String activityRegion,
    String zoneId,
    LocalDateTime approvedAt,
    List<ChartActivityResponse> activities
) {
  public static ChartEducatorResponse of(int rank, ChartEducator educator, List<ChartActivityResponse> activities) {
    return new ChartEducatorResponse(rank, educator.getEducatorId(), educator.getName(), educator.getRole(),
        educator.getAcademyName(), educator.getActivityRegion(), educator.getZoneId(), educator.getApprovedAt(), activities);
  }
}
