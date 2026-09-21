package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 네이버 프로필 응답을 {@link NaverProfileParser}로 언랩·검증해 표준 {@link OAuth2User}로 변환한다.
 *
 * <p>실제 HTTP 호출(토큰으로 {@code /v1/nid/me} 조회)은 {@link DefaultOAuth2UserService}에 위임한다 —
 * 이 클래스는 그 응답의 "response" 중첩 구조를 언랩하는 책임만 진다. STEP 1 범위: User/SocialAccount
 * 생성, JWT/Cookie 발급은 절대 하지 않는다(STEP 2~3에서 AuthenticationSuccessHandler 쪽에 추가).
 */
public class NaverOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private static final Logger log = LoggerFactory.getLogger(NaverOAuth2UserService.class);

  private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

  public NaverOAuth2UserService() {
    this(new DefaultOAuth2UserService());
  }

  NaverOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
    this.delegate = delegate;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User rawUser = delegate.loadUser(userRequest);

    Map<String, Object> response;
    try {
      response = NaverProfileParser.parseResponse(rawUser.getAttributes());
    } catch (OAuth2AuthenticationException ex) {
      log.warn("NAVER_OAUTH_FAILURE stage={}", ex.getError().getErrorCode());
      throw ex;
    }

    // providerIdPresent만 남긴다 — response.id 실제 값은 로그에 남기지 않는다.
    log.info("NAVER_OAUTH_PROFILE_OK providerIdPresent=true");

    return new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_USER")),
        response,
        NaverProfileParser.ID_KEY);
  }
}
