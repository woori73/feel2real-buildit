package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * HTTP 호출 없이 delegate를 대체해 "response" 언랩 로직만 검증한다. 실제 프로필 API 호출은
 * {@link DefaultOAuth2UserService}에 위임돼 있으므로(이 클래스 책임 밖) 여기서는 mock하지 않는다.
 */
@DisplayName("NaverOAuth2UserService")
class NaverOAuth2UserServiceTest {

  private static final ClientRegistration NAVER_REGISTRATION = ClientRegistration.withRegistrationId("naver")
      .clientId("test-client-id")
      .clientSecret("test-client-secret")
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri("https://feel2real.co.kr/login/oauth2/code/naver")
      .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
      .tokenUri("https://nid.naver.com/oauth2.0/token")
      .userInfoUri("https://openapi.naver.com/v1/nid/me")
      .userNameAttributeName("response")
      .clientName("Naver")
      .build();

  private static OAuth2UserRequest dummyRequest() {
    OAuth2AccessToken token = new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        "dummy-access-token-value-not-real",
        Instant.now(),
        Instant.now().plusSeconds(3600));
    return new OAuth2UserRequest(NAVER_REGISTRATION, token);
  }

  private static OAuth2UserService<OAuth2UserRequest, OAuth2User> delegateReturning(Map<String, Object> rawAttributes) {
    OAuth2User rawUser = new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_USER")), rawAttributes, "resultcode");
    return request -> rawUser;
  }

  @Test
  @DisplayName("response.id를 언랩해 OAuth2User attribute로 노출한다")
  void unwrapsResponseIntoOAuth2User() {
    NaverOAuth2UserService service = new NaverOAuth2UserService(delegateReturning(Map.of(
        "resultcode", "00",
        "message", "success",
        "response", Map.of("id", "naver-stable-id-789", "email", "user@example.com"))));

    OAuth2User user = service.loadUser(dummyRequest());

    assertThat(user.getName()).isEqualTo("naver-stable-id-789");
    assertThat((Object) user.getAttribute("id")).isEqualTo("naver-stable-id-789");
    assertThat((Object) user.getAttribute("email")).isEqualTo("user@example.com");
    // "response"로 감싸져 있던 원본 구조가 아니라 언랩된 평면 맵이어야 한다.
    assertThat(user.getAttributes()).doesNotContainKey("response");
  }

  @Test
  @DisplayName("'response'가 없는 프로필은 OAuth2AuthenticationException으로 실패한다")
  void throwsWhenResponseMissing() {
    NaverOAuth2UserService service = new NaverOAuth2UserService(
        delegateReturning(Map.of("resultcode", "00", "message", "success")));

    assertThatThrownBy(() -> service.loadUser(dummyRequest()))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }
}
