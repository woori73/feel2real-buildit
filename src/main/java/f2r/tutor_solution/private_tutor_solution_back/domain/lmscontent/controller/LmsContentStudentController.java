package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.StudentLessonDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.StudentLessonSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsContentDeliveryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsContentStudentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 학생(재원생)의 "내 복습수업" 조회 + 영상 스트리밍(작업지시서 §5). {@code /api/tutor/**}가 아니라
 * 로그인 회원이면 누구나 호출할 수 있는 일반 {@code /api/**}(SecurityConfig의 authenticated() catch-all)
 * — owner/재원생 구분은 {@link f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsLearnerAccessService}가
 * 서비스 레이어에서 판정한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lms/{handle}")
public class LmsContentStudentController {

  private final LmsContentStudentService studentService;
  private final LmsContentDeliveryService deliveryService;

  @GetMapping("/my/lessons")
  public ResponseEntity<List<StudentLessonSummary>> myLessons(
      @PathVariable String handle,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(studentService.listMyLessons(handle, principal));
  }

  @GetMapping("/my/lessons/{lessonId}")
  public ResponseEntity<StudentLessonDetail> lessonDetail(
      @PathVariable String handle, @PathVariable Long lessonId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(studentService.getLessonDetail(handle, lessonId, principal));
  }

  @GetMapping("/contents/{contentId}/stream")
  public ResponseEntity<StreamingResponseBody> stream(
      @PathVariable String handle, @PathVariable Long contentId,
      @RequestHeader(value = "Range", required = false) String range,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return deliveryService.get(handle, contentId, range, principal);
  }

  @RequestMapping(value = "/contents/{contentId}/stream", method = RequestMethod.HEAD)
  public ResponseEntity<Void> headStream(
      @PathVariable String handle, @PathVariable Long contentId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return deliveryService.head(handle, contentId, principal);
  }
}
