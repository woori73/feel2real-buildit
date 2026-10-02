package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto;

/** 회원 본인 비밀번호 변경 요청(강제 설정 화면과 동일 필드 — 현재 비번 검증 필수). */
public record NewStartPasswordChangeRequest(
    String currentPassword,
    String newPassword,
    String confirmPassword) {
}
