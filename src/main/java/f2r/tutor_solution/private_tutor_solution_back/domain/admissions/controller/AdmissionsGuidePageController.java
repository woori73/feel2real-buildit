package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 입시상담 가이드 화면(/admissions/guide) 렌더링.
 *
 * <p>비회원 이용 — SecurityConfig에서 permitAll.
 * 실제 상담 데이터는 {@code /api/admissions/chat} (IP 기반 rate limit 적용).
 */
@Controller
public class AdmissionsGuidePageController {

    @GetMapping("/admissions/guide")
    public String admissionsGuide(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        return "admissions/guide";
    }
    @GetMapping("/admissions/workspace")
    @PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
    public String workspace(HttpServletResponse response, Model model,
                            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        model.addAttribute("memberMode",true);
        model.addAttribute("admissionsAdmin",principal!=null && "ADMIN".equals(principal.role()));
        return "admissions/guide";
    }
}
