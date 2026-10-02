package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service.NewStartMemberPasswordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service.NewStartMemberPasswordService.PasswordPageView;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 회원의 [새 비밀번호 설정] / [비밀번호 변경] 화면 — 한 path가 두 상태를 겸한다.
 *
 * <p>ADMIN이 임시 비밀번호로 초기화한 상태({@code password_reset_required=true})에서는 서버 게이트
 * ({@link f2r.tutor_solution.private_tutor_solution_back.config.NewStartPasswordGateInterceptor})가
 * 다른 NewStart 화면·API로 가는 길목을 모두 이 페이지로 돌린다. 상태가 아닌 회원이 스스로 열어
 * {@code [비밀번호 변경]} 화면으로 쓰이기도 한다 — 검증 규칙(현재 비밀번호 확인 포함)은 동일하다.
 *
 * <p>로그인 ID는 세션 principal에서 정한다. 요청 파라미터로 대상을 지정할 수 없으므로 이 화면이
 * 남의 계정을 바꾸는 통로가 되지 않는다.
 */
@Controller
public class NewStartPasswordPageController {

  private final NewStartMemberPasswordService passwordService;

  public NewStartPasswordPageController(NewStartMemberPasswordService passwordService) {
    this.passwordService = passwordService;
  }

  @GetMapping("/newstart/password/set")
  public String setPassword(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      HttpServletResponse response,
      Model model) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    if (principal == null || principal.userId() == null) {
      return "redirect:/newstart/login";
    }
    PasswordPageView view = passwordService.pageView(principal.userId());
    model.addAttribute("loginId", view.loginId());
    model.addAttribute("changeRequired", view.changeRequired());
    model.addAttribute("minPasswordLength", view.minPasswordLength());
    return "newstart/password_set";
  }
}
