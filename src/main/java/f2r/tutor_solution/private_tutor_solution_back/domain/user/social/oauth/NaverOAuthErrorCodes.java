package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

/** 이 구현이 직접 발생시키는 네이버 OAuth2 실패 코드. Spring Security 표준 코드와 분리해 둔다. */
final class NaverOAuthErrorCodes {

  static final String MISSING_RESPONSE = "naver_missing_response";
  static final String MISSING_RESPONSE_ID = "naver_missing_response_id";

  private NaverOAuthErrorCodes() {
  }
}
