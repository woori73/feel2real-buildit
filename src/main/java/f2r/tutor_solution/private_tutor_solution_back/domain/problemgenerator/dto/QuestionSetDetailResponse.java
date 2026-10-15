package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorQuestionSet;
import java.time.LocalDateTime;
import java.util.List;

public record QuestionSetDetailResponse(
    Long setId,
    String title,
    String grammar,
    String promptPassage,
    String model,
    String rawResponseJson,
    MaterialVisibility visibility,
    Long ownerUserId,
    Long materialId,
    LocalDateTime createdAt,
    List<SavedQuestionResponse> questions) {
  public static QuestionSetDetailResponse of(ProblemGeneratorQuestionSet set, List<SavedQuestionResponse> questions) {
    return new QuestionSetDetailResponse(
        set.getSetId(), set.getTitle(), set.getGrammar(), set.getPromptPassage(), set.getModel(),
        set.getRawResponseJson(), set.getVisibility(), set.getOwner().getUserId(),
        set.getMaterial() != null ? set.getMaterial().getMaterialId() : null, set.getCreatedAt(), questions);
  }
}
