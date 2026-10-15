package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonWeekday;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonApplicationResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonApplyRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonSearchCondition;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonSearchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.service.LessonApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LessonApplicationController {

  private final LessonApplicationService lessonApplicationService;

  @GetMapping("/api/lessons/search")
  public ResponseEntity<List<LessonSearchResponse>> searchLessons(
      @RequestParam(required = false) Integer subjectId,
      @RequestParam(required = false) TargetGroup targetGroup,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String district,
      @RequestParam(required = false) LessonType lessonType,
      @RequestParam(required = false) LessonWeekday weekday,
      @RequestParam(required = false) Integer maxPrice,
      @RequestParam(required = false) Integer weeklyCount) {
    LessonSearchCondition condition = new LessonSearchCondition(
        subjectId, targetGroup, city, district, lessonType, weekday, maxPrice, weeklyCount);
    return ResponseEntity.ok(lessonApplicationService.search(condition));
  }

  @PostMapping("/api/lesson-applications")
  public ResponseEntity<LessonApplicationResponse> apply(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody LessonApplyRequest request) {
    return ResponseEntity.ok(lessonApplicationService.apply(
        principal,
        request == null ? null : request.lessonId(),
        request == null ? null : request.conversationId()));
  }

  @GetMapping("/api/lesson-applications/me")
  public ResponseEntity<List<LessonApplicationResponse>> listMine(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(lessonApplicationService.listMine(principal));
  }

  @DeleteMapping("/api/lesson-applications/{applicationId}")
  public ResponseEntity<LessonApplicationResponse> cancel(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long applicationId) {
    return ResponseEntity.ok(lessonApplicationService.cancel(principal, applicationId));
  }

  @GetMapping("/api/tutor/lesson-applications")
  public ResponseEntity<List<LessonApplicationResponse>> listTutorApplications(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(lessonApplicationService.listTutorApplications(principal));
  }

  @GetMapping("/api/tutor/lessons/{lessonId}/applications")
  public ResponseEntity<List<LessonApplicationResponse>> listLessonApplications(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId) {
    return ResponseEntity.ok(lessonApplicationService.listLessonApplications(principal, lessonId));
  }
}
