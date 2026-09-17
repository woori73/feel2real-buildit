package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyWithholdingRecord;
import java.time.LocalDate;

public record EasyWithholdingResponse(
    Long recordId,
    Long businessId,
    String businessName,
    String payee,
    long grossAmount,
    Long netAmount,
    Long withholdingAmount,
    LocalDate paidOn,
    Long txnId,
    boolean filingChecked,
    boolean statementChecked,
    String memo
) {
  public static EasyWithholdingResponse from(EasyWithholdingRecord record) {
    return new EasyWithholdingResponse(
        record.getRecordId(),
        record.getBusiness().getBusinessId(),
        record.getBusiness().getName(),
        record.getPayee(),
        record.getGrossAmount(),
        record.getNetAmount(),
        record.getWithholdingAmount(),
        record.getPaidOn(),
        record.getTxn() == null ? null : record.getTxn().getTxnId(),
        record.isFilingChecked(),
        record.isStatementChecked(),
        record.getMemo());
  }
}
