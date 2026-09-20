package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * NaverClientRegistrationConfig given/when/then — 네이버 공식 endpoint 3종이 정확히 고정됐는지,
 * userNameAttributeName이 "response"인지(V1/V2가 썼던 "result"와 다름 — gunkim/springboot-oauth2-sample
 * application-oauth.yml로 확인된 값), 그리고 미설정(client-id 없음) 시 부팅에 영향 없이 등록
 * 0건으로 대체되는지를 검증한다.
 */
class NaverClientRegistrationConfigTest {

  private final NaverClientRegistrationConfig config = new NaverClientRegistrationConfig();

  @Test
  @DisplayName("given: NAVER_CLIENT_ID/SECRET 설정됨, when: repository 생성, then: 네이버 공식 endpoint 고정")
  void configuredPropsProduceOfficialNaverEndpoints() {
    NaverOAuthProperties props = new NaverOAuthProperties("cid", "csecret", "");

    ClientRegistrationRepository repo = config.clientRegistrationRepository(props);
    ClientRegistration reg = repo.findByRegistrationId("naver");

    assertThat(reg).isNotNull();
    assertThat(reg.getClientId()).isEqualTo("cid");
    assertThat(reg.getProviderDetails().getAuthorizationUri())
        .isEqualTo("https://nid.naver.com/oauth2.0/authorize");
    assertThat(reg.getProviderDetails().getTokenUri())
        .isEqualTo("https://nid.naver.com/oauth2.0/token");
    assertThat(reg.getProviderDetails().getUserInfoEndpoint().getUri())
        .isEqualTo("https://openapi.naver.com/v1/nid/me");
    // 네이버 프로필 응답은 {resultcode, message, response:{id,...}}로 한 번 더 감싸져 있다.
    assertThat(reg.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
        .isEqualTo("response");
    assertThat(reg.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
    assertThat(reg.getAuthorizationGrantType().getValue()).isEqualTo("authorization_code");
  }

  @Test
  @DisplayName("given: client-id/secret 비어 있음, when: repository 생성, then: 등록 0건 — 예외 없이 부팅 영향 없음")
  void unconfiguredPropsYieldEmptyRepositoryWithoutThrowing() {
    NaverOAuthProperties props = new NaverOAuthProperties("", "", "");

    ClientRegistrationRepository repo = config.clientRegistrationRepository(props);

    assertThat(repo.findByRegistrationId("naver")).isNull();
  }
}
