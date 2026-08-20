package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidateStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartVerificationStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ChartCandidateResponse(
    Long candidateId,
    String zoneId,
    String name,
    String role,
    String academyName,
    String activityRegion,
    String contentType,
    String contentSummary,
    String sourceUrl,
    String sourceTitle,
    String discoveryReason,
    String deepSearchResult,
    String exclusionCheck,
    ChartVerificationStatus verificationStatus,
    ChartCandidateStatus status,
    LocalDateTime discoveredAt,
    List<ChartActivityResponse> activities
) {
  public static ChartCandidateResponse from(ChartCandidate candidate, List<ChartActivityResponse> activities) {
    return new ChartCandidateResponse(candidate.getCandidateId(), candidate.getZoneId(), candidate.getName(), candidate.getRole(),
        candidate.getAcademyName(), candidate.getActivityRegion(), candidate.getContentType(), candidate.getContentSummary(),
        candidate.getSourceUrl(), candidate.getSourceTitle(), candidate.getDiscoveryReason(), candidate.getDeepSearchResult(),
        candidate.getExclusionCheck(), candidate.getVerificationStatus(), candidate.getStatus(), candidate.getDiscoveredAt(), activities);
  }
}
