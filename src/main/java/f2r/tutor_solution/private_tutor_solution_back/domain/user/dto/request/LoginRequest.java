package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request;

public record LoginRequest(
        String loginId,
        String password
) {
}
