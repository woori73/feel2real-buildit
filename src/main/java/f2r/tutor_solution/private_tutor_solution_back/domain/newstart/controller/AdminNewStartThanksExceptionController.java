package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.ExceptionView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.Result;
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
 * 감사회원 인증 "예외함" — 운영자 API. 자동 인증이 안전하게 끝나지 못한 건만 올라온다(정상 인증은 목록에 없다).
 * 권한은 경로가 결정한다({@code /api/admin/**} = ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/admin/newstart/thanks-exceptions")
public class AdminNewStartThanksExceptionController {

  private final NewStartThanksVerificationService verificationService;

  public AdminNewStartThanksExceptionController(NewStartThanksVerificationService verificationService) {
    this.verificationService = verificationService;
  }

  @GetMapping
  public List<ExceptionView> list(@RequestParam(required = false) String status) {
    return verificationService.adminListExceptions(status);
  }

  /** 연결 승인(+THANKS_MEMBER 부여). 후보가 여러 명/미특정인 예외는 body.rosterId 로 대상을 고른다. */
  @PostMapping("/{id}/resolve")
  public ResponseEntity<AdminResponse> resolve(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long id,
      @RequestBody(required = false) ResolveRequest request) {
    try {
      return respond(verificationService.adminResolve(
          id, request == null ? null : request.rosterId(), principal == null ? null : principal.loginId()));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new AdminResponse("error", "이미 다른 계정과 연결된 명단이거나 이미 연결된 계정입니다.", null));
    }
  }

  @PostMapping("/{id}/dismiss")
  public ResponseEntity<AdminResponse> dismiss(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long id,
      @RequestBody(required = false) DismissRequest request) {
    return respond(verificationService.adminDismiss(
        id, request == null ? null : request.note(), principal == null ? null : principal.loginId()));
  }

  private static ResponseEntity<AdminResponse> respond(Result<ExceptionView> r) {
    return r.ok() ? ResponseEntity.ok(new AdminResponse("ok", null, r.value()))
        : ResponseEntity.badRequest().body(new AdminResponse("error", r.errorMessage(), null));
  }

  public record ResolveRequest(Long rosterId) {}

  public record DismissRequest(String note) {}

  public record AdminResponse(String status, String errorMessage, ExceptionView exception) {}
}
