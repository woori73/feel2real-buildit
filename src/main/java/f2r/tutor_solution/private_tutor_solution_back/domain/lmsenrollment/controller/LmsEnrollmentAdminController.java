package f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.EnrollmentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.GrantRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.LessonEnrollmentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.service.LmsEnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 강사용 LMS 수강권 배정 API(작업지시서 §6). {@code /api/tutor/**}라 hasRole("TUTOR")를 그대로 탄다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tutor/lms/{handle}/enrollments")
public class LmsEnrollmentAdminController {

  private final LmsEnrollmentService service;

  @GetMapping("/lessons/{lessonId}")
  public ResponseEntity<LessonEnrollmentView> getView(
      @PathVariable String handle, @PathVariable Long lessonId) {
    return ResponseEntity.ok(service.getView(handle, lessonId));
  }

  @PostMapping("/lessons/{lessonId}")
  public ResponseEntity<EnrollmentView> grant(
      @PathVariable String handle, @PathVariable Long lessonId, @RequestBody GrantRequest request) {
    return ResponseEntity.ok(service.grant(handle, lessonId, request));
  }

  @DeleteMapping("/lessons/{lessonId}/{lmsEnrollmentId}")
  public ResponseEntity<EnrollmentView> revoke(
      @PathVariable String handle, @PathVariable Long lessonId, @PathVariable Long lmsEnrollmentId) {
    return ResponseEntity.ok(service.revoke(handle, lessonId, lmsEnrollmentId));
  }
}
