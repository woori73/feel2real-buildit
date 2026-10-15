package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import java.util.List;

public record ChartDiscoveryRunResponse(
    boolean ready,
    String message,
    String model,
    String zoneId,
    String zoneName,
    int geminiCallCount,
    int contentDiscoveryCallCount,
    int deepSearchCallCount,
    int rawCandidateCount,
    int duplicatesMergedCount,
    int skippedExistingEducatorCount,
    int verifiedCount,
    int reviewRequiredCount,
    int excludedCount,
    int savedCount,
    List<String> webSearchQueries,
    List<ChartCandidateResponse> saved,
    List<RejectedCandidate> excluded
) {
  public record RejectedCandidate(String name, String role, String academyName, String sourceUrl, String reason) {}
}
