package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

public record ChartSettingsUpdateRequest(
    String searchZone,
    String includeActivities,
    String excludedTargets,
    Integer candidateLimit,
    Boolean deepSearchEnabled,
    String extraInstruction
) {
}
