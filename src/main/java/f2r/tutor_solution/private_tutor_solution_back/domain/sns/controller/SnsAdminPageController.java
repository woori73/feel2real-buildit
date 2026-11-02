package f2r.tutor_solution.private_tutor_solution_back.domain.sns.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsPublishService;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** 인스타 자동 피드 관리자 화면(SSR 틀 + JS). /admin/sns/** 는 SecurityConfig에서 ADMIN 전용, 여기서도 한 번 더 확인. */
@Controller
public class SnsAdminPageController {

  private final SnsPublishService publish;

  public SnsAdminPageController(SnsPublishService publish) {
    this.publish = publish;
  }

  @GetMapping("/admin/sns")
  public String home(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal, Model model, HttpServletResponse response) {
    return page(principal, "home", model, response);
  }

  @GetMapping("/admin/sns/{section:accounts|brands|rules|posts}")
  public String section(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal, @PathVariable String section,
      Model model, HttpServletResponse response) {
    return page(principal, section, model, response);
  }

  /** 인스타그램 로그인 후 돌아오는 곳. state 쿠키와 대조한 뒤 계정을 연결하고 계정 화면으로 보낸다. */
  @GetMapping("/admin/sns/accounts/callback")
  public String callback(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) String code, @RequestParam(required = false) String state,
      @RequestParam(name = "error_description", required = false) String errorDescription,
      @CookieValue(name = SnsAdminApiController.STATE_COOKIE, required = false) String expectedState,
      HttpServletResponse response) {
    requireAdmin(principal);
    ResponseCookie clear = ResponseCookie.from(SnsAdminApiController.STATE_COOKIE, "").path("/admin/sns").maxAge(0).build();
    response.addHeader(HttpHeaders.SET_COOKIE, clear.toString());
    if (errorDescription != null) return redirectWithError("인스타그램에서 연결을 취소했거나 거부했습니다.");
    if (state == null || expectedState == null
        || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), expectedState.getBytes(StandardCharsets.UTF_8))) {
      return redirectWithError("연결 요청이 만료되었거나 올바르지 않습니다. 다시 시도해 주세요.");
    }
    try {
      publish.connect(code);
      return "redirect:/admin/sns/accounts?connected=1";
    } catch (RuntimeException e) {
      return redirectWithError(e.getMessage());
    }
  }

  private static String redirectWithError(String message) {
    return "redirect:/admin/sns/accounts?error=" + URLEncoder.encode(message == null ? "연결 실패" : message, StandardCharsets.UTF_8);
  }

  private String page(JwtTokenProvider.AuthenticatedPrincipal principal, String section, Model model, HttpServletResponse response) {
    requireAdmin(principal);
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    model.addAttribute("section", section);
    return "admin/sns";
  }

  private static void requireAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운영자만 접근할 수 있습니다.");
    }
  }
}
