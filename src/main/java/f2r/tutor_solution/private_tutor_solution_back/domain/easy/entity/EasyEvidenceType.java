package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Easy 증빙 유형 — 저장만 한다. 세율 자동계산은 세법 확정 전까지 하지 않는다.
 */
public enum EasyEvidenceType {
  TAX_INVOICE,
  CREDIT_CARD,
  CASH_RECEIPT,
  INVOICE,
  NONE,
  OTHER;

  public String label() {
    return switch (this) {
      case TAX_INVOICE -> "세금계산서";
      case CREDIT_CARD -> "신용카드";
      case CASH_RECEIPT -> "현금영수증";
      case INVOICE -> "계산서";
      case NONE -> "없음";
      case OTHER -> "기타";
    };
  }

  public static EasyEvidenceType parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (EasyEvidenceType type : values()) {
      if (type.name().equals(normalized) || type.label().equals(value.trim())) {
        return type;
      }
    }
    throw new IllegalArgumentException("증빙 유형 값이 올바르지 않습니다.");
  }
}
