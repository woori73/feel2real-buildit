package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(
    @NotBlank String currentPassword
) {
}
