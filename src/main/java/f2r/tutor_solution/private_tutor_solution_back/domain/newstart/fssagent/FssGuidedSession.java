package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

/**
 * Guided Agent 사용자별 진행 상태(서버 측).
 *
 * <p>보관하는 것은 {@code userId}와 현재 단계 번호뿐입니다 — 금감원 화면에 입력되는
 * 개인정보·인증 정보·민원 본문은 이 경로에 절대 전달되지 않고 저장되지도 않는다.
 * 요청 body를 받지 않는 엔드포인트로만 갱신된다(회귀 테스트 고정).
 *
 * @param currentStep 1~14
 * @param finished    true면 14단계 확인 완료(그래도 실제 제출은 사용자가 직접 한다)
 */
public record FssGuidedSession(int currentStep, boolean finished) {

  public static FssGuidedSession start() {
    return new FssGuidedSession(1, false);
  }

  FssGuidedSession advanced() {
    if (finished) {
      return this;
    }
    return currentStep >= FssGuidedWorkflow.total()
        ? new FssGuidedSession(FssGuidedWorkflow.total(), true)
        : new FssGuidedSession(currentStep + 1, false);
  }

  FssGuidedSession retreated() {
    if (finished) {
      return new FssGuidedSession(FssGuidedWorkflow.total(), false);
    }
    return new FssGuidedSession(Math.max(1, currentStep - 1), false);
  }
}
