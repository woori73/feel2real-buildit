package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Public, non-authenticating guides that hand users to the relevant official service. */
@Controller
public class NewStartPracticalToolsPageController {

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
  public String atoz(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/a-to-z";
  }
}
