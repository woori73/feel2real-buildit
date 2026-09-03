package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionAiSummary;

import java.time.LocalDateTime;

public record QuestionAiSummaryResponse(
    Long summaryId,
    int version,
    String status,
    String summaryText,
    Integer threadPostCount,
    LocalDateTime createdAt) {

  public static QuestionAiSummaryResponse from(QuestionAiSummary s) {
    return new QuestionAiSummaryResponse(
        s.getSummaryId(),
        s.getVersion(),
        s.getStatus().name(),
        s.getSummaryText(),
        s.getThreadPostCount(),
        s.getCreatedAt());
  }
}
