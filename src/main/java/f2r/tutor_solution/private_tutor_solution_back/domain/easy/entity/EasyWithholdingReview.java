package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Easy 원천징수 검토 상태 — 신고 기능이 아니라 검토 표시다.
 * 3.3% 인적용역 같은 거래를 일반 비용과 구분하기 위한 최소 상태다.
 */
public enum EasyWithholdingReview {
  NONE,
  NEEDS_REVIEW,
  CHECKED;

  public static EasyWithholdingReview parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (EasyWithholdingReview review : values()) {
      if (review.name().equals(normalized)) {
        return review;
      }
    }
    throw new IllegalArgumentException("원천징수 검토 상태 값이 올바르지 않습니다.");
  }
}
