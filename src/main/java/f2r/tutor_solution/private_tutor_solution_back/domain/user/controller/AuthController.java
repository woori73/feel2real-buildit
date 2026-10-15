package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.LoginIdFindRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.AdminPasswordChangeRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.LoginRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.PasswordResetConfirmRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.PasswordResetRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginIdFindResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.PasswordResetRequestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.PasswordResetTokenResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminPasswordChangeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.PasswordResetService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final AdminPasswordChangeService adminPasswordChangeService;
    private final JwtTokenProvider tokenProvider;
    private final CookieUtil cookieUtil;

    /**
     * 로그인 — 비번 검증 성공 시 JWT를 HTTP-only Cookie로 발급한다.
     * 응답 body의 user 정보는 프론트의 UI 표시용 (사용자명, 역할 표시 등). 인증 자체는 쿠키로만 이뤄짐.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {
        LoginResponse user = userService.login(request);
        String token = tokenProvider.issue(user.userId(), user.loginId(), user.role());
        cookieUtil.writeAccessToken(response, token);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login-id/find")
    public ResponseEntity<LoginIdFindResponse> findLoginIds(
            @RequestBody LoginIdFindRequest request) {
        return ResponseEntity.ok(passwordResetService.findLoginIds(request.email()));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<PasswordResetRequestResponse> requestPasswordReset(
            @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        PasswordResetTokenResult result = passwordResetService.requestReset(
                request.loginId(), request.email(), baseUrl(servletRequest));
        return ResponseEntity.ok(new PasswordResetRequestResponse(
                result.email(), result.temporaryPassword()));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * 운영 ADMIN 비밀번호 변경 — {@code /admin/login} 화면의 변경 폼 전용.
     * 대상은 서버가 admin-f2r ADMIN으로 고정하므로 userId/role 지정이 불가하고,
     * 기존 비밀번호를 모르면 변경할 수 없다. 성공 시 204.
     */
    @PostMapping("/admin/password-change")
    public ResponseEntity<Void> changeAdminPassword(
            @RequestBody AdminPasswordChangeRequest request) {
        adminPasswordChangeService.changePassword(
                request.loginId(),
                request.currentPassword(),
                request.newPassword(),
                request.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    /** 로그아웃 — 쿠키를 빈 값 + Max-Age=0으로 덮어서 브라우저에서 삭제하게 한다. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        cookieUtil.clearAccessToken(response);
        return ResponseEntity.noContent().build();
    }

    /**
     * 현재 로그인 세션 정보 조회. 프론트가 새 탭/새로고침 시 sessionStorage가 비어 있어도
     * 쿠키 기반으로 본인 정보를 다시 가져올 수 있도록 제공.
     *
     * <p>인증 미설정이면 SecurityConfig에서 401로 떨어진다 — 여기까지 오면 항상 principal 존재.
     */
    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(userService.getLoginInfo(principal.userId()));
    }
}
