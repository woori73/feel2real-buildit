package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarBankAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 문법 문제은행 독립 페이지 {@code /grammar-items} 진입점.
 *
 * <p>내신위키(/problem-generator/wiki)와 분리된 별도 화면으로, 정적 SPA 인덱스를 포워딩한다.
 * 권한이 없는 로그인 사용자는 {@link GrammarBankAccessGuard}가 403으로 차단하고, 비회원은
 * SecurityConfig의 로그인 리다이렉트(returnUrl=/grammar-items)로 이동한 뒤 로그인 후 복귀한다.
 */
@Controller
@RequiredArgsConstructor
public class GrammarItemsPageController {

  private final GrammarBankAccessGuard accessGuard;

  @GetMapping({"/grammar-items", "/grammar-items/"})
  public String page(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    accessGuard.require(principal);
    return "forward:/grammar-items/index.html";
  }
}
