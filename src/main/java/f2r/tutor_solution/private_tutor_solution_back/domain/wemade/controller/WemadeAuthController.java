package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /wemade 회원 가입 API (공개).
 *
 * 기존 정책 재사용: 네이버 ID를 회원 ID로 사용(소문자 정규화) + 사이트 전용 비밀번호.
 * NewStart의 ID/PW 가입(NewStartIdPwSignupService)과 동일한 ID/PW 정책을 쓰되,
 * NewStart 전용(카페 닉네임·감사회원 명단) 로직은 포함하지 않는다.
 * 가입 성공 시 바로 로그인 쿠키를 발급한다. 로그인은 기존 POST /api/auth/login.
 * 네이버 OAuth는 사용하지 않는다 (2026-10-06 정책).
 */
@RestController
@RequestMapping("/api/wemade/auth")
@RequiredArgsConstructor
public class WemadeAuthController {

    private static final Pattern NAVER_ID = Pattern.compile("^[a-z0-9_-]{3,30}$");
    private static final int MIN_PASSWORD = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final CookieUtil cookieUtil;

    public record SignupRequest(String loginId, String password) {}

    public record SignupResponse(boolean ok, String message) {}

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<SignupResponse> signup(
            @RequestBody(required = false) SignupRequest request,
            HttpServletResponse response) {
        if (request == null || request.loginId() == null) {
            return ResponseEntity.badRequest().body(new SignupResponse(false, "입력값이 없습니다."));
        }
        String loginId = request.loginId().strip().toLowerCase(Locale.ROOT);
        if (!NAVER_ID.matcher(loginId).matches()) {
            return ResponseEntity.badRequest()
                    .body(new SignupResponse(false, "네이버 ID를 영문 소문자·숫자·_·- 3~30자로 입력해 주세요."));
        }
        if (request.password() == null || request.password().length() < MIN_PASSWORD
                || request.password().length() > 100) {
            return ResponseEntity.badRequest()
                    .body(new SignupResponse(false, "비밀번호는 " + MIN_PASSWORD + "자 이상으로 직접 정해 주세요."));
        }
        if (f2r.tutor_solution.private_tutor_solution_back.domain.user.service.ReservedLoginIds.isReserved(loginId)) {
            return ResponseEntity.badRequest()
                    .body(new SignupResponse(false, "사용할 수 없는 아이디입니다."));
        }
        if (userRepository.existsByLoginId(loginId)) {
            return ResponseEntity.status(409)
                    .body(new SignupResponse(false, "이미 가입된 아이디입니다. 로그인해 주세요."));
        }
        try {
            User user = userRepository.saveAndFlush(User.builder()
                    .loginId(loginId)
                    .password(passwordEncoder.encode(request.password()))
                    .name(loginId)
                    .nickname(loginId)
                    .role(Role.STUDENT)
                    .build());
            cookieUtil.writeAccessToken(response,
                    tokenProvider.issue(user.getUserId(), user.getLoginId(), user.getRole().name()));
        } catch (DataIntegrityViolationException e) { // 동시 가입(아이디 UNIQUE)
            return ResponseEntity.status(409)
                    .body(new SignupResponse(false, "이미 가입된 아이디입니다. 로그인해 주세요."));
        }
        return ResponseEntity.ok(new SignupResponse(true, "가입되었습니다."));
    }
}
