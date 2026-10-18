package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto.ExamRequestCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto.ExamRequestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.service.ExamRequestService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예상문제 의뢰 접수(로그인 사용자). 인증은 SecurityConfig의 {@code /api/**} catch-all이 담당한다.
 */
@RestController
@RequestMapping("/api/exam-requests")
@RequiredArgsConstructor
public class ExamRequestController {

  private final ExamRequestService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExamRequestResponse register(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody ExamRequestCreateRequest request) {
    return service.register(principal.userId(), request);
  }

  @GetMapping("/me")
  public List<ExamRequestResponse> mine(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return service.listMine(principal.userId());
  }
}
