package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeChinaDisclosureRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository.WemadeChinaNewsRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeDeltaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeIssueService;
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
    private final WemadeIssueService issueService;
    private final WemadeDeltaService deltaService;

    @GetMapping("/wemade")
    public String wemadeHome(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        String viewer = principal == null ? "GUEST" : "MEMBER";
        model.addAttribute("viewer", viewer);
        model.addAttribute("issues", issueService.forViewer(viewer));
        model.addAttribute("deltaItems", deltaService.buildDelta().stream()
                .limit("MEMBER".equals(viewer) ? 3 : 1).toList());
        return "wemade/index";
    }

    /**
     * /wemade 로그인 진입점 — ID/PW 로그인 페이지.
     * 네이버 OAuth는 사용하지 않는다 (2026-10-06 정책).
     * 기존 회원 시스템(네이버 ID를 회원 ID로 사용 + 사이트 전용 비밀번호)을 재사용하며,
     * 로그인은 기존 POST /api/auth/login, 가입은 POST /api/wemade/auth/signup.
     */
    @GetMapping("/wemade/login")
    public String wemadeLogin(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        if (principal != null) return "redirect:/wemade";
        return "wemade/login";
    }

    /**
     * 중국 공시 상세 (한국어 번역·해설) — 회원 전용.
     * 원문 전문을 복제하지 않고 한국어 해설 형태로 제공한다.
     */
    @GetMapping("/wemade/china/disclosures/{id}")
    public String chinaDisclosureDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            Model model, HttpServletResponse response) {
        if (principal == null) return "redirect:/wemade";
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
     * 중국 뉴스 상세 (한국어 번역·해설) — 회원 전용.
     * 공시와 충돌하면 공시를 우선한다는 원칙을 화면에 명시한다.
     */
    @GetMapping("/wemade/china/news/{id}")
    public String chinaNewsDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            Model model, HttpServletResponse response) {
        if (principal == null) return "redirect:/wemade";
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
