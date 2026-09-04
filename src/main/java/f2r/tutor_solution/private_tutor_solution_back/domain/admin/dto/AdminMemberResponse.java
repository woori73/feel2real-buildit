package f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.time.LocalDateTime;

/**
 * 관리자 회원 목록 항목. 안전하게 노출 가능한 값만 담는다 —
 * 비밀번호/토큰/이메일/전화 등 민감 정보는 절대 포함하지 않는다.
 */
public record AdminMemberResponse(
    Long userId,
    String displayName,
    String loginId,
    String role,
    LocalDateTime joinedAt
) {
  public static AdminMemberResponse of(User u) {
    return new AdminMemberResponse(
        u.getUserId(), u.getDisplayName(), u.getLoginId(), u.getRole().name(), u.getCreatedAt());
  }
}
