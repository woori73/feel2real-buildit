package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * NAVER는 Spring 기본 {@code CommonOAuth2Provider}에 없으므로 provider 상세를 명시 설정해야 한다
 * (docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md §18). 여기서는 property 기반
 * registration/provider 바인딩이 공식 문서(§4~6)와 정확히 일치하는지만 검증한다 — 값은 전부 dummy.
 * 실제 NAVER_CLIENT_ID/NAVER_CLIENT_SECRET 환경변수는 사용하지 않는다.
 */
@DisplayName("NAVER ClientRegistration 설정")
class NaverOAuth2ClientRegistrationConfigTest {

  private static final String[] TEST_PROPERTIES = {
      "spring.security.oauth2.client.registration.naver.client-id=test-dummy-client-id",
      "spring.security.oauth2.client.registration.naver.client-secret=test-dummy-client-secret",
      "spring.security.oauth2.client.registration.naver.redirect-uri=https://feel2real.co.kr/login/oauth2/code/naver",
      "spring.security.oauth2.client.registration.naver.authorization-grant-type=authorization_code",
      "spring.security.oauth2.client.registration.naver.client-name=Naver",
      "spring.security.oauth2.client.provider.naver.authorization-uri=https://nid.naver.com/oauth2.0/authorize",
      "spring.security.oauth2.client.provider.naver.token-uri=https://nid.naver.com/oauth2.0/token",
      "spring.security.oauth2.client.provider.naver.user-info-uri=https://openapi.naver.com/v1/nid/me",
      "spring.security.oauth2.client.provider.naver.user-name-attribute=response",
  };

  /**
   * prd yaml의 naver-wemade registration을 그대로 재현한다. 2026-10-06 배포 실패의 회귀 방지용 —
   * {@code provider: naver}가 빠지면 ApplicationContext 기동 자체가
   * IllegalStateException("Provider ID must be specified for client registration 'naver-wemade'")
   * 으로 실패한다 (journal 확인済).
   */
  private static final String[] WEMADE_TEST_PROPERTIES = {
      "spring.security.oauth2.client.registration.naver-wemade.client-id=test-dummy-client-id",
      "spring.security.oauth2.client.registration.naver-wemade.client-secret=test-dummy-client-secret",
      "spring.security.oauth2.client.registration.naver-wemade.redirect-uri=https://feel2real.co.kr/login/oauth2/code/naver-wemade",
      "spring.security.oauth2.client.registration.naver-wemade.authorization-grant-type=authorization_code",
      "spring.security.oauth2.client.registration.naver-wemade.client-name=NaverWemade",
      "spring.security.oauth2.client.registration.naver-wemade.provider=naver",
  };

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class))
      .withPropertyValues(TEST_PROPERTIES);

  private final ApplicationContextRunner wemadeContextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class))
      .withPropertyValues(TEST_PROPERTIES)
      .withPropertyValues(WEMADE_TEST_PROPERTIES);

  @Test
  @DisplayName("naver registration이 공식 엔드포인트로 정확히 바인딩된다")
  void naverRegistrationBindsToOfficialEndpoints() {
    contextRunner.run(context -> {
      ClientRegistrationRepository repository = context.getBean(ClientRegistrationRepository.class);
      ClientRegistration naver = repository.findByRegistrationId("naver");

      assertThat(naver).isNotNull();
      assertThat(naver.getProviderDetails().getAuthorizationUri())
          .isEqualTo("https://nid.naver.com/oauth2.0/authorize");
      assertThat(naver.getProviderDetails().getTokenUri())
          .isEqualTo("https://nid.naver.com/oauth2.0/token");
      assertThat(naver.getProviderDetails().getUserInfoEndpoint().getUri())
          .isEqualTo("https://openapi.naver.com/v1/nid/me");
      assertThat(naver.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
      assertThat(naver.getRedirectUri()).isEqualTo("https://feel2real.co.kr/login/oauth2/code/naver");
      // Spring 기본 convention과 일치 — 콘솔에 새 URL 등록이 필요 없다(§10 등록 근거 있는 값).
      assertThat(naver.getRedirectUri()).endsWith("/login/oauth2/code/naver");
    });
  }

  @Test
  @DisplayName("naver/naver-wemade 두 registration이 모두 정상 생성된다")
  void naverAndNaverWemadeRegistrationsAreBothCreated() {
    wemadeContextRunner.run(context -> {
      ClientRegistrationRepository repository = context.getBean(ClientRegistrationRepository.class);
      ClientRegistration naver = repository.findByRegistrationId("naver");
      ClientRegistration wemade = repository.findByRegistrationId("naver-wemade");

      assertThat(naver).isNotNull();
      assertThat(wemade).isNotNull();
      // provider: naver 바인딩 — naver-wemade가 naver provider의 엔드포인트를 그대로 쓴다.
      assertThat(wemade.getProviderDetails().getAuthorizationUri())
          .isEqualTo("https://nid.naver.com/oauth2.0/authorize");
      assertThat(wemade.getProviderDetails().getTokenUri())
          .isEqualTo("https://nid.naver.com/oauth2.0/token");
      assertThat(wemade.getProviderDetails().getUserInfoEndpoint().getUri())
          .isEqualTo("https://openapi.naver.com/v1/nid/me");
      assertThat(wemade.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
      assertThat(wemade.getRedirectUri())
          .isEqualTo("https://feel2real.co.kr/login/oauth2/code/naver-wemade");
    });
  }
}
