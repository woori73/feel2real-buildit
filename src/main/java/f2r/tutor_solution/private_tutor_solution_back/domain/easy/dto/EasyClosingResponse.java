package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAnnualClosing;

public record EasyClosingResponse(
    Long closingId,
    Long businessId,
    String businessName,
    int fiscalYear,
    String status,
    String checklistSnapshot,
    String note
) {
  public static EasyClosingResponse from(EasyAnnualClosing closing) {
    return new EasyClosingResponse(
        closing.getClosingId(),
        closing.getBusiness().getBusinessId(),
        closing.getBusiness().getName(),
        closing.getFiscalYear(),
        closing.getStatus().name(),
        closing.getChecklistSnapshot(),
        closing.getNote());
  }
}
