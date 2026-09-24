package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyLmsShellModel;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsLearnerAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsLearnerAccessService.Access;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * "복습 강의보기"(작업지시서 §5) — 기존 8개 MOCK 화면과 URL 공간을 분리한 신규 페이지다.
 * {@code /lms/{handle}/lecture/{courseId}}는 MOCK 강좌 id를 쓰므로 그대로 두고, 실제
 * {@code LessonProduct.lessonId}를 쓰는 이 화면은 별도 경로({@code /review/{lessonId}})로 둬서
 * id 공간이 섞이지 않게 한다(작업지시서 §5 "REAL·MOCK 영역을 명확하게 분리").
 *
 * <p>SecurityConfig에 이 경로 전용 매처를 추가하지 않았다 — {@code /lms/**}의 fail-closed
 * catch-all(authenticated())이 이미 이 화면을 인증 필수로 막는다. 실제 "owner 또는 수강 학생"
 * 판정은 {@link LmsLearnerAccessService}가 한다(비인가 접근은 403).
 */
@Controller
@RequiredArgsConstructor
public class LmsReviewPageController {

  private final LmsLearnerAccessService accessService;
  private final AcademyLmsShellModel shellModel;

  @GetMapping("/lms/{handle}/review/{lessonId}")
  public String review(
      @PathVariable String handle, @PathVariable Long lessonId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      Model model) {
    Access access = accessService.requireAccess(handle, lessonId, principal);
    shellModel.populate(model, access.academy(), handle);
    model.addAttribute("lessonId", lessonId);
    return "lms/lms_review";
  }
}
