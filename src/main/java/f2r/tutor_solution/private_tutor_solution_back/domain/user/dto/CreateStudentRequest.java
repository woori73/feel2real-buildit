package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto;

public record CreateStudentRequest(
        String loginId,
        String password,
        String name,
        String nickname,
        String email,
        String phone,
        String schoolName,
        Byte grade,
        String parentName,
        String parentPhone
) {
}
