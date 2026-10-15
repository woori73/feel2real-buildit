package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

public record EasyFilingReferenceRequest(
    Long businessId,
    Integer fiscalYear,
    Long revenue,
    Long expense,
    Long incomeAmount,
    String source,
    String note
) {
}
