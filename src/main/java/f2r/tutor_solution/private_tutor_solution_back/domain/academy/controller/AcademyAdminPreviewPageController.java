package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 관리자용 대상 학원 비공개 미리보기 페이지. 공개 페이지와 같은 템플릿·렌더러를 쓰되 데이터는 관리자 작업본
 * 미리보기 API({@code /api/admin/academies/{id}/builder/preview})에서 읽고 방문 통계는 기록하지 않는다.
 * 접근 제한은 SecurityConfig의 ADMIN 매처가 맡는다(이 컨트롤러는 학원 존재만 확인).
 */
@Controller
@RequiredArgsConstructor
public class AcademyAdminPreviewPageController {

  private final AcademyRepository academyRepository;

  @GetMapping("/admin/academies/{academyId}/preview")
  public String preview(@PathVariable Long academyId, HttpServletResponse response, Model model) {
    var academy = academyRepository.findById(academyId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Robots-Tag", "noindex, nofollow");
    model.addAttribute("slug", academy.getSlug());
    model.addAttribute("previewMode", "working-copy");
    model.addAttribute("previewApi", "/api/admin/academies/" + academyId + "/builder/preview");
    return "public/academy_public";
  }
}
