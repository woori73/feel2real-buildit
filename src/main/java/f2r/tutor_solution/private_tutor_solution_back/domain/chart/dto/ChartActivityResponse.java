package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidateActivity;
import java.time.LocalDateTime;

public record ChartActivityResponse(
    Long activityId,
    String contentType,
    String contentSummary,
    String sourceUrl,
    LocalDateTime discoveredAt
) {
  public static ChartActivityResponse from(ChartCandidateActivity activity) {
    return new ChartActivityResponse(activity.getActivityId(), activity.getContentType(), activity.getContentSummary(),
        activity.getSourceUrl(), activity.getDiscoveredAt());
  }
}
