package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/** Easy 연도 마감 상태 — 마감 후에도 해제→수정→재마감이 가능하다. */
public enum EasyClosingStatus {
  OPEN,
  REVIEW,
  CLOSED
}
