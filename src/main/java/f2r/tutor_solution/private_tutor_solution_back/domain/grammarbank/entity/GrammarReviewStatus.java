package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

/** 검수 상태. 자동 분류 결과만 있으면 AUTO/NEEDS_REVIEW, 사람 판정 후 APPROVED/REJECTED. */
public enum GrammarReviewStatus {
  AUTO, NEEDS_REVIEW, APPROVED, REJECTED
}
