package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto;

public record CreateTutorRequest(
    String loginId,
    String password,
    String name,
    String nickname,
    String email,
    String phone
) {
}
