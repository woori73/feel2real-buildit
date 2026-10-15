package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Easy staging 행 상태 — DUPLICATE는 장부 중복 의심으로 확정에서 제외한다.
 * 중복 판정이 아니라 확정 제외 표시이며, 판단·합치기는 사용자가 한다.
 */
public enum EasyImportRowStatus {
  PENDING,
  READY,
  ERROR,
  IMPORTED,
  DUPLICATE
}
