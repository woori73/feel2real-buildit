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
    String receiptRef
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
        txn.getReceiptRef());
  }
}
