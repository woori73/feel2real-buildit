package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGrantService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGrantService.GrantResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGrantService.GrantView;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사회원(THANKS_MEMBER) 권한 부여·회수 운영자 API.
 *
 * <p>권한은 경로가 결정한다 — {@code /api/admin/**}는 SecurityConfig에서 {@code hasRole('ADMIN')}이라
 * 이 클래스 안에 추가 검사 규칙을 두지 않는다(AdminNewStartRequestController와 같은 규칙).
 *
 * <p>부여 대상은 숫자 user_id만 받고, 응답에도 user_id·capability·운영자 표식만 담는다(email·실명 등
 * PII는 이 경로를 통과하지 않는다). row 1개가 곧 권한이므로 회수는 DELETE 한 번이고, 부여 직후 회원의
 * 토큰·재로그인은 필요 없다 — 판정은 매 요청에서 grant table을 보기 때문이다.
 */
@RestController
@RequestMapping("/api/admin/newstart/grants")
public class AdminNewStartGrantController {

  private final NewStartGrantService grantService;

  public AdminNewStartGrantController(NewStartGrantService grantService) {
    this.grantService = grantService;
  }

  @GetMapping
  public List<GrantView> list(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return grantService.list();
  }

  @PostMapping
  public ResponseEntity<GrantResponse> grant(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody(required = false) GrantRequest request) {
    GrantResult result = grantService.grant(
        request == null ? null : request.userId(),
        request == null ? null : request.capability(),
        principal == null ? null : principal.loginId());
    return respond(result);
  }

  @DeleteMapping("/{userId}")
  public ResponseEntity<GrantResponse> revoke(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long userId,
      @RequestParam(required = false) NewStartCapability capability) {
    return respond(grantService.revoke(userId, capability));
  }

  private static ResponseEntity<GrantResponse> respond(GrantResult result) {
    return result.ok()
        ? ResponseEntity.ok(GrantResponse.success(result.grant()))
        : ResponseEntity.badRequest().body(GrantResponse.failure(result.errorMessage()));
  }

  /** @param userId users.user_id. @param capability 생략 시 THANKS_MEMBER. */
  public record GrantRequest(Long userId, NewStartCapability capability) {}

  public record GrantResponse(String status, String errorMessage, GrantView grant) {
    static GrantResponse success(GrantView view) {
      return new GrantResponse("ok", null, view);
    }

    static GrantResponse failure(String errorMessage) {
      return new GrantResponse("error", errorMessage, null);
    }
  }
}
