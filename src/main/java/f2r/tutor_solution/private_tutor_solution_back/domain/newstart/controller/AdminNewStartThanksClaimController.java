package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService.AdminClaimView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService.Result;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사회원 본인확인 요청 — 운영자 API. 권한은 경로가 결정한다({@code /api/admin/**} = ROLE_ADMIN, SecurityConfig).
 * 승인만이 roster 연결과 THANKS_MEMBER 부여를 일으킨다.
 */
@RestController
@RequestMapping("/api/admin/newstart/thanks-claims")
public class AdminNewStartThanksClaimController {

  private final NewStartThanksClaimService claimService;

  public AdminNewStartThanksClaimController(NewStartThanksClaimService claimService) {
    this.claimService = claimService;
  }

  @GetMapping
  public List<AdminClaimView> list(@RequestParam(required = false) String status) {
    return claimService.adminList(status);
  }

  @PostMapping("/{claimId}/approve")
  public ResponseEntity<AdminResponse> approve(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long claimId) {
    try {
      return respond(claimService.approve(claimId, principal == null ? null : principal.loginId()));
    } catch (DataIntegrityViolationException e) {
      // roster/회원 1:1 UNIQUE(V1017) 위반 — 동시 승인 경합. 내부 SQL 상세는 노출하지 않는다.
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new AdminResponse("error", "이미 다른 회원과 연결된 명단이거나 이미 연결된 회원입니다.", null));
    }
  }

  @PostMapping("/{claimId}/reject")
  public ResponseEntity<AdminResponse> reject(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long claimId,
      @RequestBody(required = false) RejectRequest request) {
    return respond(claimService.reject(claimId, request == null ? null : request.reason(),
        principal == null ? null : principal.loginId()));
  }

  private static ResponseEntity<AdminResponse> respond(Result<AdminClaimView> r) {
    return r.ok() ? ResponseEntity.ok(new AdminResponse("ok", null, r.value()))
        : ResponseEntity.badRequest().body(new AdminResponse("error", r.errorMessage(), null));
  }

  public record RejectRequest(String reason) {}

  public record AdminResponse(String status, String errorMessage, AdminClaimView claim) {}
}
