package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/**
 * 매통조 OpenAPI 인증 실패 (HTTP 401/403, 토큰 거부, 이용기관 권한 없음).
 * 메시지에 "401"/"인증" 문구를 유지한다 —
 * {@code EasyCollectionSchedulerService}의 인증실패 분류(isAuthFailure)가
 * 이 문구로 NEEDS_REAUTH를 판정하기 때문이다.
 */
public class CrefiaAuthException extends RuntimeException {

  public CrefiaAuthException(String message) {
    super(message);
  }

  public CrefiaAuthException(String message, Throwable cause) {
    super(message, cause);
  }
}
