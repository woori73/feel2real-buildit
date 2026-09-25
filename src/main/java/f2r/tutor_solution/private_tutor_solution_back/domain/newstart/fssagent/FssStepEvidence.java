package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

/**
 * Guided Agent 단계 판정 근거 등급.
 *
 * <ul>
 *   <li>{@link #POC_VERIFIED} — 2026-09-24 HOME READ-ONLY POC(headless Chromium)에서 화면·控件까지
 *       직접 확인한 단계 (STEP 1~4, handoff §3).
 *   <li>{@link #POC_SCREEN_VERIFIED} — 진입 화면 자체는 POC에서 확인했으나, 그 화면에서의
 *       실제 진행(개인정보 입력·본인인증)이 인증 경계라 READ-ONLY POC에서 멈췄다 (STEP 5~7).
 *   <li>{@link #MANUAL_UNVERIFIED} — 카페 매뉴얼(2961) 텍스트 기준, 현재 FSS 화면 미검증 (STEP 8~14).
 *       자동화·임의 selector 금지, 사용자에게差异 가능성을 고지한다.
 * </ul>
 */
public enum FssStepEvidence {
  POC_VERIFIED,
  POC_SCREEN_VERIFIED,
  MANUAL_UNVERIFIED
}
