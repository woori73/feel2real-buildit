package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request;

/**
 * 운영 ADMIN 비밀번호 변경 요청 — {@code /admin/login} 화면의 변경 폼 전용.
 *
 * <p>대상 계정은 서버가 고정(admin-f2r ADMIN)으로 결정한다. 요청에 userId/role이 없으므로
 * 클라이언트가 다른 사용자를 지정할 수 없다. 평문은 로그·응답에 절대 포함하지 않는다.
 */
public record AdminPasswordChangeRequest(
        String loginId,
        String currentPassword,
        String newPassword,
        String confirmPassword
) {
}
