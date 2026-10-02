package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response;

/**
 * 회원 셀프 재설정 결과 — 1회용·만료 재설정 링크만 담는다. 비밀번호 값은 여기에도 없다.
 *
 * <p>ADMIN이 회원을 초기화하는 {@code /api/admin/newstart/members/{id}/password-reset}과 별개로,
 * 이 경로는 비밀번호를 즉시 바꾸지 않고 링크 소유자가 직접 새 비밀번호를 정한다.
 */
public record PasswordResetTokenResult(String email, String resetUrl) {
}
