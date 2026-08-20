package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyProfileViewController {

  @GetMapping("/my/profile")
  public String myProfile(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal != null && "ADMIN".equals(principal.role())) {
      return "redirect:/home";
    }
    return "shared/my_profile";
  }
}
