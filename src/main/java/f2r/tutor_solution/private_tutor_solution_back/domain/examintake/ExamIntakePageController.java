package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakePageController {
  private final boolean newRequestsEnabled;

  public ExamIntakePageController() {
    this(false);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ExamIntakePageController(
      @org.springframework.beans.factory.annotation.Value("${app.exam-intake.new-requests-enabled:false}")
          boolean newRequestsEnabled) {
    this.newRequestsEnabled = newRequestsEnabled;
  }

  @GetMapping("/exam-service")
  public String page(@AuthenticationPrincipal AuthenticatedPrincipal p, Model model,
      @RequestParam(name = "sample", defaultValue = "") String sample) {
    if (p == null) return "redirect:/?login=required&returnUrl=%2Fexam-service"
        + ("1".equals(sample) ? "%3Fsample%3D1" : "");
    model.addAttribute("adminView", false);
    // 2026-11-23: 새 신청을 받지 않으면 신청서를 숨기고 서비스 도입 문의로 안내한다(기존 접수 확인은 그대로).
    model.addAttribute("acceptingNew", newRequestsEnabled);
    return "public/exam_service";
  }

  @GetMapping("/admin/hub/exam-requests")
  public String adminPage(@AuthenticationPrincipal AuthenticatedPrincipal p, Model model) {
    admin(p);
    model.addAttribute("adminView", true);
    return "admin/exam_requests";
  }
}

