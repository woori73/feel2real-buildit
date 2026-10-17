package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminCredentialService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN 본인 계정(로그인 ID·비밀번호) 교체. {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class AdminAccountController {

  public record CredentialRotationRequest(
      String currentPassword, String newLoginId, String confirmLoginId, String newPassword, String confirmPassword) {}

  private final AdminCredentialService service;
  private final CookieUtil cookieUtil;

  /** 성공 시 현재 세션 쿠키를 지우고 204. 사용자는 새 ID·비밀번호로 다시 로그인한다. */
  @PostMapping("/api/admin/account/credentials")
  public ResponseEntity<Void> rotate(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody CredentialRotationRequest request,
      HttpServletResponse response) {
    service.rotate(principal.userId(), request.currentPassword(), request.newLoginId(),
        request.confirmLoginId(), request.newPassword(), request.confirmPassword());
    cookieUtil.clearAccessToken(response);
    return ResponseEntity.noContent().build();
  }
}
