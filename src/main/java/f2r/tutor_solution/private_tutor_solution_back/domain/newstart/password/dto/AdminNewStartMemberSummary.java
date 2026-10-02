package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto;

import java.time.LocalDateTime;

/**
 * ADMIN 회원 검색 결과 1행.
 *
 * <p>비밀번호 원문·hash는 이 화면의 어떤 필드에도 존재하지 않는다 — 표시할 데이터가 아니고,
 * 필요해도 되돌릴 수 없으므로 조회 기능 자체를 만들지 않는다.
 *
 * @param memberType        {@code THANKS_MEMBER}(감사회원) / {@code GENERAL}(일반회원)
 * @param accountStatus     {@code ACTIVE} / {@code RESET_REQUIRED}(임시 비밀번호 상태)
 * @param lastLoginAt       도입 이후 로그인 기록. 없으면 null(과거 이력은 소급 생성하지 않는다)
 */
public record AdminNewStartMemberSummary(
    Long userId,
    String loginId,
    String cafeNickname,
    String memberType,
    String accountStatus,
    LocalDateTime joinedAt,
    LocalDateTime lastLoginAt
) {
  public static final String THANKS = "THANKS_MEMBER";
  public static final String GENERAL = "GENERAL";
  public static final String ACTIVE = "ACTIVE";
  public static final String RESET_REQUIRED = "RESET_REQUIRED";
}
