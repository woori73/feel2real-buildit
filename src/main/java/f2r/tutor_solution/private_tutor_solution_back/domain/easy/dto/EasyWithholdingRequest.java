package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;

public record EasyWithholdingRequest(
    Long businessId,
    String payee,
    Long grossAmount,
    Long netAmount,
    Long withholdingAmount,
    LocalDate paidOn,
    Long txnId,
    Boolean filingChecked,
    Boolean statementChecked,
    String memo
) {
}
