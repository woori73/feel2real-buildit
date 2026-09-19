package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 네이버 OAuth 2.0 앱 설정. 값은 환경변수로 주입하며 secret은 절대 커밋하지 않는다.
 *
 * <p>{@code clientId}/{@code clientSecret} 중 하나라도 비어 있으면 네이버 로그인은
 * 비활성 상태로 취급한다 — 부팅은 정상 진행되고 start endpoint만 안내 후 /newstart로 되돌린다.
 *
 * @param clientId 네이버 개발자센터 Application Client ID ({@code NAVER_CLIENT_ID})
 * @param clientSecret Application Secret ({@code NAVER_CLIENT_SECRET}) — 서버 전용
 * @param redirectUri 콜백 절대 URL ({@code NAVER_OAUTH_REDIRECT_URI}). 비우면 요청
 *                    base URL + /oauth/naver/callback으로 자동 산출(로컬 개발 편의).
 *                    운영에서는 개발자센터 등록 값과 정확히 일치하는 값을 명시해야 한다.
 */
@ConfigurationProperties(prefix = "app.naver.oauth")
public record NaverOAuthProperties(
    String clientId,
    String clientSecret,
    String redirectUri
) {

  public boolean configured() {
    return clientId != null && !clientId.isBlank()
        && clientSecret != null && !clientSecret.isBlank();
  }
}
