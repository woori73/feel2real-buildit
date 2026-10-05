package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * /wemade 페이지. NewStart/EASY와 완전히 별도 도메인.
 * Cache-Control: no-store (로그인 상태에 따라 달라지는 HTML).
 */
@Controller
@RequiredArgsConstructor
public class WemadePageController {

    @GetMapping("/wemade")
    public String wemadeHome(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        model.addAttribute("viewer", principal == null ? "GUEST" : "MEMBER");
        return "wemade/index";
    }

    /**
     * /wemade 로그인 진입점. naver-wemade registration이 있으면 사용하고,
     * 없으면(네이버 콘솔 redirect URI 미등록 등) 기존 naver 플로우로 폴백한다.
     * 기존 OAuth 핸들러/리다이렉트는 건드리지 않는다.
     */
    @GetMapping("/wemade/login")
    public String wemadeLogin(
            org.springframework.beans.factory.ObjectProvider<
                    org.springframework.security.oauth2.client.registration.ClientRegistrationRepository>
                    repoProvider) {
        var repo = repoProvider.getIfAvailable();
        if (repo != null) {
            try {
                if (repo.findByRegistrationId("naver-wemade") != null) {
                    return "redirect:/oauth2/authorization/naver-wemade";
                }
            } catch (Exception ignored) { /* 폴백 */ }
        }
        return "redirect:/oauth2/authorization/naver";
    }
}
