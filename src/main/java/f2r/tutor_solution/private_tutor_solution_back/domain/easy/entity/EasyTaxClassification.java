package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Easy 세무구분 — 회계 분류(direction/account)와 분리 저장한다.
 * 세법 확정 전에는 코드가 최종 세무판정을 단정하지 않는다.
 * NULL(REVIEW_REQUIRED 취급)이 기본이며, 사용자 승인 후에만 확정값으로 둔다.
 */
public enum EasyTaxClassification {
  /** 총수입에 포함되는 매출. */
  INCOME_INCLUDED,
  /** 필요경비 공제 후보 (사업관련성·당시 세법 확인 필요). */
  EXPENSE_DEDUCTIBLE_CANDIDATE,
  /** 필요경비 불인정. */
  NON_DEDUCTIBLE,
  /** 총수입이 아님 (예: 대출금 입금, 계좌이체 수취). */
  NOT_INCOME,
  /** 필요경비가 아님 (예: 대출원금 상환, 계좌이체 지급). */
  NOT_EXPENSE,
  /** 미분류 — 자동 posting 금지, 관리자 검토 대상. */
  REVIEW_REQUIRED
}
