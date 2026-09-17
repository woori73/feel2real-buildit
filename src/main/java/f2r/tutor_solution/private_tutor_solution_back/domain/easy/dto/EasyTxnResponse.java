package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import java.time.LocalDate;

public record EasyTxnResponse(
    Long txnId,
    Long businessId,
    String businessName,
    LocalDate occurredOn,
    String direction,
    long amount,
    String category,
    String counterparty,
    String description,
    String payMethod,
    String memo,
    String receiptRef,
    Long accountId,
    String accountName,
    Long supplyAmount,
    Long vatAmount,
    String evidenceType,
    String sourceKind,
    String sourceRef,
    String fingerprint,
    String evidenceReview,
    String withholdingReview,
    Boolean expenseReversal
) {
  public static EasyTxnResponse from(EasyTransaction txn) {
    return new EasyTxnResponse(
        txn.getTxnId(),
        txn.getBusiness().getBusinessId(),
        txn.getBusiness().getName(),
        txn.getOccurredOn(),
        txn.getDirection().name(),
        txn.getAmount(),
        txn.getCategory(),
        txn.getCounterparty(),
        txn.getDescription(),
        txn.getPayMethod(),
        txn.getMemo(),
        txn.getReceiptRef(),
        txn.getAccount() == null ? null : txn.getAccount().getAccountId(),
        txn.getAccount() == null ? null : txn.getAccount().getName(),
        txn.getSupplyAmount(),
        txn.getVatAmount(),
        txn.getEvidenceType() == null ? null : txn.getEvidenceType().name(),
        txn.getSourceKind(),
        txn.getSourceRef(),
        txn.getFingerprint(),
        txn.getEvidenceReview() == null ? null : txn.getEvidenceReview().name(),
        txn.getWithholdingReview() == null ? null : txn.getWithholdingReview().name(),
        txn.isReversal() ? Boolean.TRUE : null);
  }
}
