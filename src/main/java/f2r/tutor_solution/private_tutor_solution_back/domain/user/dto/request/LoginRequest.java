package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request;

/**
 * 아이디·비밀번호 로그인 요청.
 *
 * @param rememberMe "로그인 상태 유지" 체크 여부. true면 로그인 쿠키를 길게(30일) 발급한다. 없으면(null) false로 본다.
 */
public record LoginRequest(
        String loginId,
        String password,
        Boolean rememberMe
) {
    public LoginRequest(String loginId, String password) {
        this(loginId, password, null);
    }

    public boolean rememberMeChecked() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
