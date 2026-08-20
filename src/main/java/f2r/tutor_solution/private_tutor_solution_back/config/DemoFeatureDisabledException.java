package f2r.tutor_solution.private_tutor_solution_back.config;

/**
 * Claude API 연동 기능이 비활성화(anthropic.enabled=false)됐을 때, 해당 기능을 호출하면 던진다.
 * 데모 단계에서는 전 환경(prd 포함)이 기본 비활성이라 어디서나 발생할 수 있고,
 * anthropic.enabled=true로 override한 환경에서만 발생하지 않는다.
 *
 * <p>{@link GlobalExceptionHandler}가 503 + 안내 메시지로 변환한다.
 */
public class DemoFeatureDisabledException extends RuntimeException {

  public DemoFeatureDisabledException(String message) {
    super(message);
  }
}
