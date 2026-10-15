package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자가 대상 학원 홈페이지를 관리하는 화면. 대상 academyId는 경로에 명시되며 화면의 모든 API 호출이 같은 id를 쓴다.
 * 접근 제한은 SecurityConfig의 ADMIN 매처가 맡고, 이 컨트롤러는 학원 존재만 확인한다(감사 기록은 화면 진입 API가 남긴다).
 */
@Controller
@RequiredArgsConstructor
public class AcademyAdminBuilderPageController {

  static final String VIEW = "admin/academy_manage";

  private final AcademyRepository academyRepository;

  @GetMapping("/admin/academies/{academyId}/builder")
  public String page(@PathVariable Long academyId, HttpServletResponse response, Model model) {
    if (!academyRepository.existsById(academyId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다.");
    }
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Robots-Tag", "noindex, nofollow");
    model.addAttribute("academyId", academyId);
    return VIEW;
  }
}
