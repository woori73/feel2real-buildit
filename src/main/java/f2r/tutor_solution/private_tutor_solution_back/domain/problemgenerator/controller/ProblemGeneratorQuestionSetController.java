package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.ProblemGeneratorQuestionSetService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProblemGeneratorQuestionSetController {
  private final ProblemGeneratorQuestionSetService service;

  @PostMapping("/api/problem-generator/question-sets")
  public ResponseEntity<QuestionSetDetailResponse> save(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody QuestionSetSaveRequest request) {
    QuestionSetDetailResponse saved = service.save(principal, request);
    return ResponseEntity.created(URI.create("/api/problem-generator/question-sets/" + saved.setId())).body(saved);
  }

  @GetMapping("/api/problem-generator/question-sets")
  public ResponseEntity<Page<QuestionSetSummaryResponse>> list(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(service.list(principal, pageable));
  }

  @GetMapping("/api/problem-generator/question-sets/{setId}")
  public ResponseEntity<QuestionSetDetailResponse> detail(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long setId) {
    return ResponseEntity.ok(service.detail(principal, setId));
  }

  @PatchMapping("/api/problem-generator/question-sets/{setId}")
  public ResponseEntity<QuestionSetSummaryResponse> updateSet(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long setId,
      @RequestBody QuestionSetUpdateRequest request) {
    return ResponseEntity.ok(service.updateSet(principal, setId, request));
  }

  @PatchMapping("/api/problem-generator/question-sets/{setId}/questions/{questionId}")
  public ResponseEntity<SavedQuestionResponse> updateQuestion(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long setId,
      @PathVariable Long questionId,
      @RequestBody QuestionUpdateRequest request) {
    return ResponseEntity.ok(service.updateQuestion(principal, setId, questionId, request));
  }

  @DeleteMapping("/api/problem-generator/question-sets/{setId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long setId) {
    service.delete(principal, setId);
    return ResponseEntity.noContent().build();
  }
}
