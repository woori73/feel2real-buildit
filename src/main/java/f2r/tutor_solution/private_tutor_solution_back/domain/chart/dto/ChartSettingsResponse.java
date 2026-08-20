package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoverySettings;

public record ChartSettingsResponse(
    String searchZone,
    String includeActivities,
    String excludedTargets,
    int candidateLimit,
    boolean deepSearchEnabled,
    String extraInstruction,
    boolean geminiApiKeyConfigured,
    String geminiApiKeySource,
    String llmProvider,
    boolean llmApiKeyConfigured,
    String llmApiKeySource,
    String llmModel
) {
  public static ChartSettingsResponse from(ChartDiscoverySettings settings, boolean llmApiKeyConfigured,
      String llmApiKeySource, String llmProvider, String llmModel) {
    return new ChartSettingsResponse(settings.getSearchZone(), settings.getIncludeActivities(), settings.getExcludedTargets(),
        settings.getCandidateLimit(), settings.isDeepSearchEnabled(), settings.getExtraInstruction(),
        llmApiKeyConfigured, llmApiKeySource, llmProvider, llmApiKeyConfigured, llmApiKeySource, llmModel);
  }
}
