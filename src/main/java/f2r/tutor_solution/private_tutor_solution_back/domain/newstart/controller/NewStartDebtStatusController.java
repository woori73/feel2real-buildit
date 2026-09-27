package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService.DebtStatusRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService.DebtStatusView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart "본인채무 확인 상태창" 조회/정리 API — {@code /newstart#ns-status-panel}과
 * {@code /newstart/tools/credit4u}의 CTA가 같은 데이터를 공유한다.
 *
 * <p>인증은 기존 {@code /api/**} catch-all(인증 필요)을 그대로 따른다. 대상 회원은 항상
 * principal(userId)로만 결정되고 요청에 userId를 받지 않는다 — 남의 상태창을 읽거나 쓰는
 * 경로가 구조적으로 없다. Credit4U ID/PW·인증서 비밀번호·OTP는 이 API가 받지도 저장하지도 않는다.
 */
@RestController
@RequestMapping("/api/newstart/debt-status")
public class NewStartDebtStatusController {

  private final NewStartMemberDebtStatusService debtStatusService;

  public NewStartDebtStatusController(NewStartMemberDebtStatusService debtStatusService) {
    this.debtStatusService = debtStatusService;
  }

  @GetMapping("/me")
  public ResponseEntity<DebtStatusView> me(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(debtStatusService.findMine(principal.userId()));
  }

  @PutMapping
  public ResponseEntity<?> save(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody DebtStatusRequest request) {
    try {
      return ResponseEntity.ok(debtStatusService.save(principal.userId(), request));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }
  }

  public record ErrorResponse(String errorMessage) {}
}
