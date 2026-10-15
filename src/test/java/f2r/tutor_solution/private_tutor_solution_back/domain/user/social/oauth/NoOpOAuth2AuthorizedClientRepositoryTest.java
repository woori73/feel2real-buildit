package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

@DisplayName("NoOpOAuth2AuthorizedClientRepository")
class NoOpOAuth2AuthorizedClientRepositoryTest {

  private static final ClientRegistration NAVER = ClientRegistration.withRegistrationId("naver")
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

  @Test
  @DisplayName("save를 호출해도 access token을 어디에도 남기지 않는다 — load는 항상 null")
  void neverPersistsAnAuthorizedClient() {
    NoOpOAuth2AuthorizedClientRepository repository = new NoOpOAuth2AuthorizedClientRepository();
    Authentication principal = new UsernamePasswordAuthenticationToken("naver-user", null);
    OAuth2AccessToken token = new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER, "should-never-be-stored", Instant.now(), Instant.now().plusSeconds(3600));
    OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(NAVER, "naver-user", token);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    repository.saveAuthorizedClient(authorizedClient, principal, request, response);

    assertThat(repository.<OAuth2AuthorizedClient>loadAuthorizedClient("naver", principal, request)).isNull();
    // 세션 기반 기본 구현과 달리, 저장 목적만으로 세션이 생성되지 않는다.
    assertThat(request.getSession(false)).isNull();
  }

  @Test
  @DisplayName("remove도 그냥 무시한다(예외 없음)")
  void removeIsNoOp() {
    NoOpOAuth2AuthorizedClientRepository repository = new NoOpOAuth2AuthorizedClientRepository();
    Authentication principal = new UsernamePasswordAuthenticationToken("naver-user", null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    repository.removeAuthorizedClient("naver", principal, request, response);
  }
}
