package f2r.tutor_solution.private_tutor_solution_back.domain.user.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;

public record UserResponse(
        Long userId,
        String loginId,
        String name,
        String nickname,
        String displayName,
        String phone,
        String role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getName(),
                user.getNickname(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRole().name()
        );
    }
}
