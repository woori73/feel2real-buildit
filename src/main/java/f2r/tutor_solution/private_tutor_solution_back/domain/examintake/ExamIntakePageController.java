package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakePageController {
  @GetMapping("/exam-service")
  public String page(@AuthenticationPrincipal AuthenticatedPrincipal p, Model model) {
    if (p == null) return "redirect:/?login=required&returnUrl=%2Fexam-service";
    model.addAttribute("adminView", false);
    return "public/exam_service";
  }

  @GetMapping("/admin/hub/exam-requests")
  public String adminPage(@AuthenticationPrincipal AuthenticatedPrincipal p, Model model) {
    admin(p);
    model.addAttribute("adminView", true);
    return "public/exam_service";
  }
}
