package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyFilingReference;

public record EasyFilingReferenceResponse(
    Long refId,
    Long businessId,
    String businessName,
    int fiscalYear,
    Long revenue,
    Long expense,
    Long incomeAmount,
    String source,
    String note
) {
  public static EasyFilingReferenceResponse from(EasyFilingReference ref) {
    return new EasyFilingReferenceResponse(
        ref.getRefId(),
        ref.getBusiness().getBusinessId(),
        ref.getBusiness().getName(),
        ref.getFiscalYear(),
        ref.getRevenue(),
        ref.getExpense(),
        ref.getIncomeAmount(),
        ref.getSource(),
        ref.getNote());
  }
}
