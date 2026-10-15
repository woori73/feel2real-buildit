package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Easy 증빙 검토 상태 — 세법 판정이 아니라 "확인 필요" 표시다.
 * 금액만으로 비용 인정/가산세를 판정하지 않는다.
 */
public enum EasyEvidenceReview {
  UNKNOWN,
  NEEDS_REVIEW,
  VERIFIED,
  MISSING,
  NOT_REQUIRED;

  public static EasyEvidenceReview parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (EasyEvidenceReview review : values()) {
      if (review.name().equals(normalized)) {
        return review;
      }
    }
    throw new IllegalArgumentException("증빙 검토 상태 값이 올바르지 않습니다.");
  }
}
