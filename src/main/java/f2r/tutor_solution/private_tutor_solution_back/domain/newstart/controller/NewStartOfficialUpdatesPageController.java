package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class NewStartOfficialUpdatesPageController {

  @GetMapping("/newstart/updates")
  public String updates(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/updates";
  }

  @GetMapping("/admin/newstart/updates")
  public String adminUpdates(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      HttpServletResponse response) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운영자만 접근할 수 있습니다.");
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "admin/newstart_updates";
  }
}
