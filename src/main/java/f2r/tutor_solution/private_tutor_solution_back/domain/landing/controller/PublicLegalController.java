package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Public policy navigation; /privacy remains the existing Chrome extension document. */
@Controller
public class PublicLegalController {

  @GetMapping("/privacy")
  public String privacy() {
    return "public/privacy";
  }

  @GetMapping("/policies")
  public String policies() {
    return "public/policies";
  }

  @GetMapping("/faq")
  public String faq() {
    return "public/faq";
  }
}
