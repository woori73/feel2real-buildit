package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto;

import java.time.LocalDateTime;

/**
 * 회원 본인 비밀번호 화면 상태.
 *
 * @param changeRequired  ADMIN이 임시 비밀번호로 초기화했는지. true면 이 화면이 강제 진입 대상이고,
 *                        {@code /newstart/password/set}로 리다이렉트된다
 * @param minPasswordLength 현재 NewStart 가입 정책과 같은 최소 길이(8)
 * @param lastLoginAt     최근 로그인 시각(이 컬럼 도입 이후만. 없으면 null)
 */
public record NewStartPasswordStatus(
    boolean changeRequired,
    int minPasswordLength,
    LocalDateTime lastLoginAt) {
}
