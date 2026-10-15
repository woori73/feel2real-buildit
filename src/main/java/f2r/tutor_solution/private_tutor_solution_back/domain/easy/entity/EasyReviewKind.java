package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Easy 장부 할 일 종류 — AI 판정이 아니라 "확인 필요" 분류다.
 */
public enum EasyReviewKind {
  UNCLASSIFIED,
  EVIDENCE,
  BUSINESS,
  WITHHOLDING,
  DUPLICATE_SUSPECT,
  IMPORT_UNCONFIRMED,
  MISSING_EXPENSE;

  public String label() {
    return switch (this) {
      case UNCLASSIFIED -> "계정과목 미분류";
      case EVIDENCE -> "증빙 확인 필요";
      case BUSINESS -> "사업장 확인 필요";
      case WITHHOLDING -> "원천징수 확인";
      case DUPLICATE_SUSPECT -> "중복 의심";
      case IMPORT_UNCONFIRMED -> "장부 미입력";
      case MISSING_EXPENSE -> "대응 비용 없음";
    };
  }
}
