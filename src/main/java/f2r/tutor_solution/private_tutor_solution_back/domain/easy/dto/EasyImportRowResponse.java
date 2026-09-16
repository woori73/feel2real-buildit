package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import java.time.LocalDate;

public record EasyImportRowResponse(
    Long rowId,
    int rowNo,
    LocalDate occurredOn,
    String direction,
    Long amount,
    String counterparty,
    String description,
    String payMethod,
    Long supplyAmount,
    Long vatAmount,
    String evidenceType,
    String status,
    String error,
    Long matchedAccountId,
    String matchedAccountName,
    Double confidence,
    Long createdTxnId
) {
  public static EasyImportRowResponse from(EasyImportRow row) {
    return new EasyImportRowResponse(
        row.getRowId(),
        row.getRowNo(),
        row.getOccurredOn(),
        row.getDirection() == null ? null : row.getDirection().name(),
        row.getAmount(),
        row.getCounterparty(),
        row.getDescription(),
        row.getPayMethod(),
        row.getSupplyAmount(),
        row.getVatAmount(),
        row.getEvidenceType() == null ? null : row.getEvidenceType().name(),
        row.getStatus().name(),
        row.getError(),
        row.getMatchedAccount() == null ? null : row.getMatchedAccount().getAccountId(),
        row.getMatchedAccount() == null ? null : row.getMatchedAccount().getName(),
        row.getConfidence(),
        row.getCreatedTxn() == null ? null : row.getCreatedTxn().getTxnId());
  }
}
