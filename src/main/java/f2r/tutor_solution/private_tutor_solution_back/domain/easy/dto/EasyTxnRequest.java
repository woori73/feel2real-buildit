package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import java.time.LocalDate;

public record EasyTxnRequest(
    Long businessId,
    LocalDate occurredOn,
    String direction,
    Long amount,
    String category,
    String counterparty,
    String description,
    String payMethod,
    String memo,
    String receiptRef,
    Long accountId,
    Long supplyAmount,
    Long vatAmount,
    String evidenceType,
    String sourceKind,
    String sourceRef,
    String evidenceReview,
    String withholdingReview
) {
  /** v1 호환 — 확장 필드 없이 호출할 때 사용. */
  public EasyTxnRequest(
      Long businessId,
      LocalDate occurredOn,
      String direction,
      Long amount,
      String category,
      String counterparty,
      String description,
      String payMethod,
      String memo,
      String receiptRef) {
    this(businessId, occurredOn, direction, amount, category, counterparty,
        description, payMethod, memo, receiptRef, null, null, null, null,
        null, null, null, null);
  }

  /** V974 호환 — V975 검토 필드 없이 호출할 때 사용. */
  public EasyTxnRequest(
      Long businessId,
      LocalDate occurredOn,
      String direction,
      Long amount,
      String category,
      String counterparty,
      String description,
      String payMethod,
      String memo,
      String receiptRef,
      Long accountId,
      Long supplyAmount,
      Long vatAmount,
      String evidenceType) {
    this(businessId, occurredOn, direction, amount, category, counterparty,
        description, payMethod, memo, receiptRef, accountId, supplyAmount, vatAmount,
        evidenceType, null, null, null, null);
  }
}
