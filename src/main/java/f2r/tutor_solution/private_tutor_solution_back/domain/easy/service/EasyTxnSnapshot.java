package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;

/**
 * Easy 거래 스냅샷 — 수정 감사 로그용 before/after JSON.
 * Jackson 없이 수동 직렬화한다 (감사 로그는 고정 필드만 담는다).
 */
public final class EasyTxnSnapshot {

  private EasyTxnSnapshot() {
  }

  public static String of(EasyTransaction txn) {
    return of(txn, null, null, null, null, null);
  }

  public static String of(EasyTransaction txn, Long businessId, Long accountId,
      String evidenceReview, String withholdingReview) {
    return of(txn, businessId, accountId, evidenceReview, withholdingReview, null);
  }

  /**
   * 미리보기 스냅샷 — null 덮어쓰기는 현재 값을 쓴다 (적용하지 않는다).
   * AI 수정 계약의 before/after 표시용이다.
   */
  public static String of(EasyTransaction txn, Long businessId, Long accountId,
      String evidenceReview, String withholdingReview, String taxClassification) {
    if (txn == null) {
      return "{}";
    }
    return "{\"txnId\":" + txn.getTxnId()
        + ",\"businessId\":" + (businessId == null ? businessId(txn) : businessId)
        + ",\"occurredOn\":\"" + txn.getOccurredOn() + "\""
        + ",\"direction\":\"" + txn.getDirection() + "\""
        + ",\"amount\":" + txn.getAmount()
        + ",\"category\":\"" + esc(txn.getCategory()) + "\""
        + ",\"counterparty\":\"" + esc(txn.getCounterparty()) + "\""
        + ",\"description\":\"" + esc(txn.getDescription()) + "\""
        + ",\"accountId\":" + (accountId == null
            ? (txn.getAccount() == null ? "null" : txn.getAccount().getAccountId()) : accountId)
        + ",\"sourceRef\":" + strOrNull(txn.getSourceRef())
        + ",\"evidenceType\":" + strOrNull(txn.getEvidenceType() == null ? null
            : txn.getEvidenceType().name())
        + ",\"evidenceReview\":" + strOrNull(evidenceReview == null
            ? (txn.getEvidenceReview() == null ? null : txn.getEvidenceReview().name())
            : evidenceReview)
        + ",\"withholdingReview\":" + strOrNull(withholdingReview == null
            ? (txn.getWithholdingReview() == null ? null : txn.getWithholdingReview().name())
            : withholdingReview)
        + ",\"taxClassification\":" + strOrNull(taxClassification == null
            ? (txn.getTaxClassification() == null ? null : txn.getTaxClassification().name())
            : taxClassification)
        + "}";
  }

  private static Long businessId(EasyTransaction txn) {
    try {
      return txn.getBusiness() == null ? null : txn.getBusiness().getBusinessId();
    } catch (Exception e) {
      return null;
    }
  }

  private static String strOrNull(String value) {
    return value == null ? "null" : "\"" + esc(value) + "\"";
  }

  private static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", " ").replace("\r", " ");
  }
}
