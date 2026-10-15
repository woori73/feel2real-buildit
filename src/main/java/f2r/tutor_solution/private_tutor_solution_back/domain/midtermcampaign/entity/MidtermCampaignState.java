package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

/**
 * 캠페인 진행 단계. 시험이 끝나도 페이지를 폐기하지 않고 상태만 전이한다
 * (작업지시서 §28). Public 페이지가 단계별로 다른 섹션을 강조한다.
 */
public enum MidtermCampaignState {
  /** 준비 중 — 기본. 학교 컬렉션·준비상태·자료를 전면에 둔다. */
  PREPARING,
  /** 시험 직전 주 — "직전 대비" 자료를 강조. */
  FINAL_WEEK,
  /** 시험 종료 — AFTER THE EXAM 영역 활성(Phase 2 Public UI). */
  EXAM_DONE,
  /** 다음 시험 모집 전환 — NEXT EXAM CTA 를 상단으로. */
  NEXT_EXAM
}
