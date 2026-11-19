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
    private final f2r.tutor_solution.private_tutor_solution_back.domain.user.service.TemporaryPasswordService temporaryPasswordService;
    private final JwtTokenProvider tokenProvider;
    private final CookieUtil cookieUtil;
    private final org.springframework.beans.factory.ObjectProvider<
            f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator> adminSessionValidator;

    /**
     * 로그인 — 비번 검증 성공 시 JWT를 HTTP-only Cookie로 발급한다.
     * 응답 body의 user 정보는 프론트의 UI 표시용 (사용자명, 역할 표시 등). 인증 자체는 쿠키로만 이뤄짐.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {
        LoginResponse user = userService.login(request);
        writeLoginCookie(response, user, request.rememberMeChecked());
        return ResponseEntity.ok(user);
    }

    /**
     * 임시 비밀번호로 로그인한 회원의 새 비밀번호 설정. 성공하면 일반 로그인 쿠키로 바꿔 준다.
     * 임시 비밀번호 로그인 토큰은 이 API·설정 화면·로그아웃만 쓸 수 있다(PasswordChangeGate).
     */
    @PostMapping("/password-change/temporary")
    public ResponseEntity<LoginResponse> changeTemporaryPassword(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestBody TemporaryPasswordChangeRequest request,
            HttpServletResponse response) {
        var user = temporaryPasswordService.changeTemporaryPassword(
                principal.userId(), request.newPassword(), request.confirmPassword());
        LoginResponse body = LoginResponse.from(user);
        writeLoginCookie(response, body);
        return ResponseEntity.ok(body);
    }

    /** ADMIN 토큰에만 자격 증명 stamp를 넣는다(ID·비밀번호 교체 시 기존 ADMIN 세션 무효화). 임시 비밀번호 상태면 설정 전용 토큰. */
    private void writeLoginCookie(HttpServletResponse response, LoginResponse user) {
        writeLoginCookie(response, user, false);
    }

    /**
     * rememberMe=true면 토큰과 쿠키를 {@link #REMEMBER_ME_SECONDS}(30일) 동안 유지한다. 임시 비밀번호 로그인은
     * 설정 전용 토큰이므로 길게 주지 않는다.
     */
    private void writeLoginCookie(HttpServletResponse response, LoginResponse user, boolean rememberMe) {
        var validator = "ADMIN".equals(user.role()) ? adminSessionValidator.getIfAvailable() : null;
        String stamp = validator == null ? null : validator.stampFor(user.userId());
        long ttl = rememberMe && !user.passwordChangeRequired() ? REMEMBER_ME_SECONDS : tokenProvider.expirationSeconds();
        String token = tokenProvider.issue(user.userId(), user.loginId(), user.role(), stamp, user.passwordChangeRequired(), ttl);
        cookieUtil.writeAccessToken(response, token, ttl);
    }

    /** "로그인 상태 유지" 유지 기간: 30일. */
    static final long REMEMBER_ME_SECONDS = 30L * 24 * 60 * 60;

    public record TemporaryPasswordChangeRequest(String newPassword, String confirmPassword) {}

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
