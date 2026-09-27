package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

/** NewStart capability 미부여 회원이 실행 전용 기능을 호출했을 때. */
public class NewStartEntitlementDeniedException extends RuntimeException {

  public NewStartEntitlementDeniedException() {
    super(NewStartEntitlementService.DENIAL_MESSAGE);
  }
}
