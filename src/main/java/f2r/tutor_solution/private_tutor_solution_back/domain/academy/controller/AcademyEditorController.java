package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * BuildIt 시각 편집기 페이지 컨트롤러.
 *
 * <p>기존 회원가입·로그인·인증 방식을 그대로 사용한다.
 * 일반 회원(MEMBER)·TUTOR는 본인 홈페이지, ADMIN은 고객 홈페이지를 편집할 수 있다.
 */
@Controller
@RequestMapping("/tutor/academy")
@RequiredArgsConstructor
public class AcademyEditorController {

  private final AcademyService academyService;

  /**
   * 시각 편집기 페이지를 표시한다.
   *
   * @param principal 인증된 사용자
   * @param academyId ADMIN이 고객 홈페이지를 지정할 경우 (선택)
   * @param model 뷰 모델
   * @return 편집기 템플릿
   */
  @GetMapping("/editor")
  public String editor(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) Long academyId,
      Model model) {
    AcademyResponse academy = resolveAcademy(principal, academyId);

    model.addAttribute("academyId", academy.academyId());
    model.addAttribute("academyName", academy.name());
    // 기존 홈페이지(P0) 불러오기는 브라우저가 공개 페이지(/academies/{slug})를 렌더해
    // 스냅샷으로 가져온다(visual_editor_import.js). 서버는 slug만 내려준다.
    model.addAttribute("academySlug", academy.slug());

    return "tutor/academy_editor";
  }

  private AcademyResponse resolveAcademy(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long academyId) {
    // ADMIN이 고객 홈페이지를 지정한 경우에도 본인 학원만 허용
    // (향후 AcademyService에 ID 조회 메서드 추가 시 확장)
    // 현재는 일반 회원/TUTOR는 본인 학원만
    return academyService.getMine(principal)
        .orElseThrow(() -> new AccessDeniedException("편집할 홈페이지가 없습니다."));
  }
}
