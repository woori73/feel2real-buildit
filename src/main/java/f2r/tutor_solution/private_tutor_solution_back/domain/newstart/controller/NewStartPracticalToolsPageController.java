package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozCaseService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Public, non-authenticating guides that hand users to the relevant official service. */
@Controller
public class NewStartPracticalToolsPageController {

  private final NewStartAtozCaseService atozCaseService;

  public NewStartPracticalToolsPageController(NewStartAtozCaseService atozCaseService) {
    this.atozCaseService = atozCaseService;
  }

  @GetMapping("/newstart/tools/{tool}")
  public String tool(@PathVariable String tool, Model model, HttpServletResponse response) {
    if (!java.util.Set.of("credit4u", "application", "status", "court").contains(tool)) {
      return "redirect:/newstart";
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    model.addAttribute("tool", tool);
    return "newstart/practical-tool";
  }

  @GetMapping("/newstart/a-to-z")
  public String atoz(Model model, HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    // §3: 단계별 "이 단계의 실제 사례" — 운영 DB ACTIVE 항목의 읽기 전용 표시 계층(없으면 빈 맵).
    model.addAttribute("casesByStep", atozCaseService.casesByStep());
    return "newstart/a-to-z";
  }
}
