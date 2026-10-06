package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeChinaDisclosureRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeChinaNewsRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * /wemade 페이지. NewStart/EASY와 완전히 별도 도메인.
 * Cache-Control: no-store (로그인 상태에 따라 달라지는 HTML).
 */
@Controller
@RequiredArgsConstructor
public class WemadePageController {

    private final WemadeChinaDisclosureRepository chinaDisclosureRepository;
    private final WemadeChinaNewsRepository chinaNewsRepository;

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

    /**
     * 중국 공시 상세 (한국어 번역·해설).
     * 원문 전문을 복제하지 않고 한국어 해설 형태로 제공한다.
     */
    @GetMapping("/wemade/china/disclosures/{id}")
    public String chinaDisclosureDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            Model model, HttpServletResponse response) {
        var d = chinaDisclosureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        model.addAttribute("viewer", principal == null ? "GUEST" : "MEMBER");
        model.addAttribute("kind", "disclosure");
        model.addAttribute("kindKr", "중국 공식 공시");
        model.addAttribute("item", d);
        return "wemade/china-detail";
    }

    /**
     * 중국 뉴스 상세 (한국어 번역·해설).
     * 공시와 충돌하면 공시를 우선한다는 원칙을 화면에 명시한다.
     */
    @GetMapping("/wemade/china/news/{id}")
    public String chinaNewsDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            Model model, HttpServletResponse response) {
        var n = chinaNewsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        model.addAttribute("viewer", principal == null ? "GUEST" : "MEMBER");
        model.addAttribute("kind", "news");
        model.addAttribute("kindKr", "중국 뉴스");
        model.addAttribute("item", n);
        return "wemade/china-detail";
    }
}
