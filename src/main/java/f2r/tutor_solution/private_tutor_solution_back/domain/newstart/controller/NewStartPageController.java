package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;

/**
 * NewStart 공개 페이지(&#47;newstart) 렌더링.
 *
 * <p>개인채무조정 상황 관리, NewStart AI, 최신 정보, 지식 라이브러리 진입점을 제공하는 MVP 페이지다.
 * 아직 백엔드 개인 데이터는 연결되지 않았으므로 공개 페이지로 제공하며, 실제 로그인/개인 데이터는
 * 향후 단계에서 연결한다.
 *
 * <p>WikiPageController 와 동일하게 {@code Cache-Control: no-store}만 위해 컨트롤러로 둔다.
 * 앞단 캐시가 계정 상태에 따라 달라질 수 있는 HTML을 공유하지 않게 하려는 목적이다.
 */
@Controller
@RequiredArgsConstructor
public class NewStartPageController {

  private final NewStartOfficialUpdateService officialUpdateService;
  private final NewStartEntitlementService entitlementService;

  @GetMapping("/newstart")
  public String newstartHome(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      Model model, HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    // 지피지기 입력창은 감사회원(THANKS_MEMBER)·ADMIN에게만 렌더한다 — 권한 없는 회원이 입력 후 403을 겪지 않게 한다.
    // 실제 차단은 계속 API(NewStartEntitlementService)가 한다. 이 값은 화면 구성용이다.
    model.addAttribute("viewer", viewerState(principal));
    model.addAttribute("latestUpdates", officialUpdateService.latestPublished(3));
    return "newstart/index";
  }

  /** GUEST(비로그인) / MEMBER(일반) / THANKS(감사회원) / ADMIN. */
  private String viewerState(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) return "GUEST";
    if ("ADMIN".equals(principal.role())) return "ADMIN";
    return entitlementService.isThanksMember(principal) ? "THANKS" : "MEMBER";
  }
}
