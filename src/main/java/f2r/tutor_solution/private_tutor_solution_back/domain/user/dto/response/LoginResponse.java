package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;

public record LoginResponse(
        Long userId,
        String loginId,
        String name,
        String nickname,
        String displayName,
        String role
) {
    public static LoginResponse from(User user) {
        return new LoginResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getName(),
                user.getNickname(),
                user.getDisplayName(),
                user.getRole().name()
        );
    }
}
