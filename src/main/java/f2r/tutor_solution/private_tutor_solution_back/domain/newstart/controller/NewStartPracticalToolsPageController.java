package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozCaseService;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

  /**
   * A to Z는 운영자(ADMIN)의 내부 상담 참고자료다(2026-10 정책). 회원·비회원이 URL을 직접 입력해도
   * 서버가 홈으로 돌려보낸다 — 링크를 숨기는 것만으로 처리하지 않는다. 단계 분류(atoz_step)·사례 데이터는 그대로다.
   */
  @GetMapping("/newstart/a-to-z")
  public String atoz(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      Model model, HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    if (principal == null || !"ADMIN".equals(principal.role())) {
      return "redirect:/newstart";
    }
    // §3: 단계별 "이 단계의 실제 사례" — 운영 DB ACTIVE 항목의 읽기 전용 표시 계층(없으면 빈 맵).
    model.addAttribute("casesByStep", atozCaseService.casesByStep());
    return "newstart/a-to-z";
  }
}
