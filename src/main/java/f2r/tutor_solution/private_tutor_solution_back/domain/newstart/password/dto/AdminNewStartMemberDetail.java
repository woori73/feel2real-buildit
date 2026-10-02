package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto;

import java.time.LocalDateTime;

/**
 * ADMIN 회원 상세. {@link AdminNewStartMemberSummary}에 초기화 판단에 필요한 운영 정보만 더한다.
 *
 * @param passwordResetRequired 현재가 임시 비밀번호 상태인지(초기화 요구)
 * @param lastResetAt           이 회원 대상 마지막 ADMIN 초기화 시각. 없으면 null
 * @param lastResetBy           그 초기화를 실행한 ADMIN 로그인 ID. 없으면 null
 * @param thanksRosterLinked    감사회원 명단 1:1 연결 row가 살아있는지(초기화 후 보존 확인용)
 */
public record AdminNewStartMemberDetail(
    Long userId,
    String loginId,
    String cafeNickname,
    String memberType,
    String accountStatus,
    LocalDateTime joinedAt,
    LocalDateTime lastLoginAt,
    boolean passwordResetRequired,
    LocalDateTime lastResetAt,
    String lastResetBy,
    boolean thanksRosterLinked
) {
}
