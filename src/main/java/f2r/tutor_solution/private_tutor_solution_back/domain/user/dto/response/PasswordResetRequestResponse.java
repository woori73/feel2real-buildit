package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response;

public record PasswordResetRequestResponse(String email, String resetUrl) {
}
