package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 작업본 비공개 미리보기 페이지. 공개 홈페이지와 같은 템플릿·렌더러를 쓰되,
 * 데이터는 작업본 미리보기 API에서 읽고 방문 통계는 기록하지 않는다.
 * 본인(TUTOR) 홈페이지만 열리며 검색엔진·캐시에 남지 않게 한다.
 */
@Controller
@RequiredArgsConstructor
public class AcademyWorkingCopyPreviewPageController {

  private final AcademyService academyService;

  @GetMapping("/tutor/academy/preview")
  public String preview(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      HttpServletResponse response,
      Model model) {
    var academy = academyService.getMine(principal);
    if (academy.isEmpty()) {
      return "redirect:/builder";
    }
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Robots-Tag", "noindex, nofollow");
    model.addAttribute("slug", academy.get().slug());
    model.addAttribute("previewMode", "working-copy");
    return "public/academy_public";
  }
}
