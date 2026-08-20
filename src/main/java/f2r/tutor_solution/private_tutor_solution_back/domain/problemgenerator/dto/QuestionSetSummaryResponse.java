package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorQuestionSet;
import java.time.LocalDateTime;

public record QuestionSetSummaryResponse(
    Long setId,
    String title,
    String model,
    MaterialVisibility visibility,
    Long ownerUserId,
    Long materialId,
    LocalDateTime createdAt) {
  public static QuestionSetSummaryResponse of(ProblemGeneratorQuestionSet set) {
    return new QuestionSetSummaryResponse(
        set.getSetId(),
        set.getTitle(),
        set.getModel(),
        set.getVisibility(),
        set.getOwner().getUserId(),
        set.getMaterial() != null ? set.getMaterial().getMaterialId() : null,
        set.getCreatedAt());
  }
}
