package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService.Candidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService.MyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService.Result;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사회원 최초 1회 본인확인 — 회원 쪽 API. 로그인(네이버 계정 연결) 회원만 쓸 수 있고(`/api/**` catch-all 401),
 * 어떤 호출도 THANKS_MEMBER를 직접 부여하지 않는다 — 요청(PENDING)만 만든다. 부여는 ADMIN 승인 경로뿐이다.
 */
@RestController
@RequestMapping("/api/newstart/thanks-claim")
public class NewStartThanksClaimController {

  private final NewStartThanksClaimService claimService;

  public NewStartThanksClaimController(NewStartThanksClaimService claimService) {
    this.claimService = claimService;
  }

  @GetMapping("/status")
  public ResponseEntity<MyStatus> status(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(claimService.status(principal == null ? null : principal.userId()));
  }

  @PostMapping("/candidates")
  public ResponseEntity<CandidatesResponse> candidates(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestBody(required = false) CandidatesRequest request) {
    Result<List<Candidate>> r = claimService.candidates(
        principal == null ? null : principal.userId(), request == null ? null : request.nickname());
    return r.ok() ? ResponseEntity.ok(new CandidatesResponse("ok", null, r.value()))
        : ResponseEntity.badRequest().body(new CandidatesResponse("error", r.errorMessage(), List.of()));
  }

  @PostMapping
  public ResponseEntity<ClaimResponse> submit(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestBody(required = false) SubmitRequest request) {
    Result<MyStatus> r = claimService.submit(
        principal == null ? null : principal.userId(), request == null ? null : request.rosterId());
    return respond(r);
  }

  @DeleteMapping
  public ResponseEntity<ClaimResponse> cancel(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return respond(claimService.cancel(principal == null ? null : principal.userId()));
  }

  private static ResponseEntity<ClaimResponse> respond(Result<MyStatus> r) {
    return r.ok() ? ResponseEntity.ok(new ClaimResponse("ok", null, r.value()))
        : ResponseEntity.badRequest().body(new ClaimResponse("error", r.errorMessage(), null));
  }

  public record CandidatesRequest(String nickname) {}

  public record SubmitRequest(Long rosterId) {}

  public record CandidatesResponse(String status, String errorMessage, List<Candidate> candidates) {}

  public record ClaimResponse(String status, String errorMessage, MyStatus claim) {}
}
