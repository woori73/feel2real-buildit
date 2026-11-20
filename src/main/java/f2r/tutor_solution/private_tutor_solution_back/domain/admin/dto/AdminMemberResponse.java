package f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
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
    if (u.getLoginId().matches("naver_[0-9a-f]{32}")) {
      String name = u.getName();
      String displayName = name == null || name.isBlank() || "네이버 회원".equals(name)
          ? "네이버 회원 (이름 미등록)" : name;
      return new AdminMemberResponse(
          u.getUserId(), displayName, "회원번호 " + u.getUserId(), u.getRole().name(),
          KstTime.toKst(u.getCreatedAt()));
    }
    return new AdminMemberResponse(
        u.getUserId(), u.getDisplayName(), u.getLoginId(), u.getRole().name(), KstTime.toKst(u.getCreatedAt()));
  }
}
