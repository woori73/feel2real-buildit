package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/testprep")
@RequiredArgsConstructor
public class TestPrepApiController {

  private final TestPrepService testPrepService;

  @GetMapping("/exams")
  public List<ExamListItem> exams(@RequestParam(required = false) String kind) {
    return testPrepService.listExams(kind);
  }

  @PostMapping("/attempts")
  public AttemptView start(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody StartAttemptRequest req) {
    return testPrepService.start(principal, req);
  }

  @GetMapping("/attempts/{attemptId}")
  public AttemptView resume(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long attemptId) {
    return testPrepService.resume(principal, attemptId);
  }

  @PutMapping("/attempts/{attemptId}/answers")
  public ResponseEntity<Void> saveAnswer(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long attemptId, @RequestBody SaveAnswerRequest req) {
    testPrepService.saveAnswer(principal, attemptId, req);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/attempts/{attemptId}/submit")
  public Scorecard submit(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long attemptId, @RequestBody(required = false) SubmitRequest req) {
    return testPrepService.submit(principal, attemptId, req);
  }

  @GetMapping("/attempts/{attemptId}/review")
  public ReviewView review(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long attemptId) {
    return testPrepService.review(principal, attemptId);
  }

  @GetMapping("/attempts")
  public List<AttemptSummary> history(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return testPrepService.history(principal);
  }

  @GetMapping("/learning")
  public LearningView learning(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return testPrepService.learning(principal);
  }
}
