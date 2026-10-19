package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

public record GrammarVariantResponse(
    Long variantId,
    Long baseQuestionId,
    Long variantQuestionId,
    String status,
    String changeReason,
    String reviewNote
) {
}
