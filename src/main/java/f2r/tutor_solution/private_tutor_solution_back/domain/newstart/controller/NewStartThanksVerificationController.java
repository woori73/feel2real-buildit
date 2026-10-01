package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksPreregistrationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.Outcome;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사회원 셀프 인증 — 회원 쪽 API(로그인 필수, {@code /api/**} catch-all 401).
 *
 * <p>회원은 카페 닉네임만 1회 입력한다. 서버가 닉네임·네이버 이메일(@naver.com)·연결 상태를 모두 확인해 자동 인증하고,
 * 안전하게 끝낼 수 없으면 운영자 예외로 넘긴다. ADMIN 은 이 절차를 거치지 않는다(state=ADMIN, 아무것도 만들지 않음).
 * 응답에는 어느 항목이 틀렸는지 드러내는 정보(접두부·이메일 등)를 담지 않는다.
 */
@RestController
@RequestMapping("/api/newstart/thanks-verification")
public class NewStartThanksVerificationController {

  private final NewStartThanksVerificationService verificationService;
  private final NewStartThanksPreregistrationService preregistrationService;

  public NewStartThanksVerificationController(NewStartThanksVerificationService verificationService,
      NewStartThanksPreregistrationService preregistrationService) {
    this.verificationService = verificationService;
    this.preregistrationService = preregistrationService;
  }

  /** 사전등록 회원: OAuth 이메일이 등록값과 정확히 일치하면 입력 없이 자동 연결+부여. 동시 경합(UNIQUE)이면 조용히 무시한다. */
  private Outcome autoPreregistered(Long userId, String role) {
    try {
      return preregistrationService.tryAutoVerify(userId, role).orElse(null);
    } catch (DataIntegrityViolationException e) {
      return null;
    }
  }

  @GetMapping("/status")
  public ResponseEntity<Outcome> status(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Long userId = principal == null ? null : principal.userId();
    String role = principal == null ? null : principal.role();
    Outcome pre = autoPreregistered(userId, role);
    return ResponseEntity.ok(pre != null ? pre : verificationService.status(userId, role));
  }

  @PostMapping
  public ResponseEntity<Outcome> verify(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestBody(required = false) VerifyRequest request) {
    Long userId = principal == null ? null : principal.userId();
    String role = principal == null ? null : principal.role();
    String nickname = request == null ? null : request.nickname();
    Outcome outcome = autoPreregistered(userId, role);
    if (outcome != null) return ResponseEntity.ok(outcome);
    try {
      outcome = verificationService.verify(userId, role, nickname);
    } catch (DataIntegrityViolationException e) {
      // 동시 인증 경합 — roster/계정 1:1 UNIQUE(V1017)가 막았다. 별도 트랜잭션으로 예외를 남기고 운영자에게 알린다.
      outcome = verificationService.recordConflict(userId, nickname);
    }
    return "ERROR".equals(outcome.state()) ? ResponseEntity.badRequest().body(outcome) : ResponseEntity.ok(outcome);
  }

  public record VerifyRequest(String nickname) {}
}
