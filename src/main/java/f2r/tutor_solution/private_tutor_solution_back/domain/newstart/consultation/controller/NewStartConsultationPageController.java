package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 감사회원 1:1 비공개 상담 화면 진입점. 감사회원(THANKS_MEMBER)·ADMIN만 진입하고,
 * 권한 없으면 감사회원 인증 안내로 보낸다. 직접 URL 접근도 여기서 차단한다.
 * 실제 상담 데이터는 {@code /api/newstart/consultations}(인증 + 감사회원 판정)에서만 내려온다.
 */
@Controller
public class NewStartConsultationPageController {

  private final NewStartEntitlementService entitlementService;

  public NewStartConsultationPageController(NewStartEntitlementService entitlementService) {
    this.entitlementService = entitlementService;
  }

  @GetMapping("/newstart/consultations")
  public String consultations(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    if (!entitlementService.isThanksMember(principal)) {
      return "redirect:/newstart/thanks-guide";
    }
    return "newstart/consultations";
  }
}
