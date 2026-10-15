package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity;

/** 베타 의견 모더레이션 상태. 랜딩엔 APPROVED만 노출한다. */
public enum BetaCommentStatus {
  PENDING,
  APPROVED,
  HIDDEN
}
