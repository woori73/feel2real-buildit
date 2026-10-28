package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import f2r.tutor_solution.private_tutor_solution_back.domain.admin.controller.AdminController;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.LandingPageController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(
    assignableTypes = {
      LandingPageController.class,
      AdminController.class,
      ExamIntakePageController.class
    })
public class ExamIntakeAvailabilityAdvice {
  private final boolean enabled;

  @Value("${app.exam-intake.bank-admin-enabled:false}")
  private boolean bankAdminEnabled;

  @ModelAttribute("examBankAdminEnabled")
  public boolean bankAdminEnabled() {
    return bankAdminEnabled;
  }

  public ExamIntakeAvailabilityAdvice(@Value("${app.exam-intake.enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  @ModelAttribute("examIntakeEnabled")
  public boolean enabled() {
    return enabled;
  }
}
