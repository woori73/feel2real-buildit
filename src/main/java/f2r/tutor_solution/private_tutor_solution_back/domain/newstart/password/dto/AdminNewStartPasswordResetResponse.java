package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto;

import java.time.LocalDateTime;

/**
 * ADMIN 초기화 실행 결과. {@code temporaryPassword}는 이 응답에서만 한 번 존재한다.
 *
 * <p>DB에는 hash만 들어가고 이 값 자체는 저장되지 않으므로, 응답을 닫고 나면 ADMIN도 다시 조회할 수
 * 없다. 재조회 엔드포인트를 만들지 않는 것이 "한 번만 표시"의 실제 구현이다.
 */
public record AdminNewStartPasswordResetResponse(
    Long userId,
    String loginId,
    String cafeNickname,
    String temporaryPassword,
    LocalDateTime resetAt
) {
}
