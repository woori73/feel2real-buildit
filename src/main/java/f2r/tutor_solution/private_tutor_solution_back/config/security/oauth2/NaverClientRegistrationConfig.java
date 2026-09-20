package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * NewStart 네이버 로그인 — 공식 Spring Security OAuth2 Client 표준 흐름 전용
 * {@code ClientRegistrationRepository}. 이 bean이 있어야만 {@code /oauth2/authorization/naver}·
 * {@code /login/oauth2/code/naver}가 동작한다(V1 {@code /oauth/naver/*}, V2
 * {@code /auth/naver/v2/*}의 손수 작성한 state/callback 코드와는 완전히 무관 — 섞지 않는다).
 *
 * <p>네이버 공식 문서 기준 endpoint만 쓴다:
 * authorize=https://nid.naver.com/oauth2.0/authorize,
 * token=https://nid.naver.com/oauth2.0/token,
 * userinfo=https://openapi.naver.com/v1/nid/me. {@code userNameAttributeName("response")}는
 * 네이버 프로필 응답이 {@code {resultcode, message, response:{id,...}}} 형태로 실제 필드를
 * "response" 아래에 한 번 더 감싸기 때문이다(참고: gunkim/springboot-oauth2-sample의
 * application-oauth.yml provider.naver.user-name-attribute 값으로 확인됨 — V1/V2가 프로필
 * 파싱에 썼던 "result" 키는 이 값과 다르다).
 *
 * <p>application.yaml의 {@code spring.security.oauth2.client.registration.*} 자동 바인딩은
 * 쓰지 않는다 — {@code client-id}가 비어 있으면 {@code ClientRegistration.build()}가
 * "clientId cannot be empty"로 예외를 던져 그 경로로는 로컬/CI처럼 {@code NAVER_CLIENT_ID}가
 * 없는 환경에서 앱 부팅 자체가 실패한다. 기존 {@link NaverOAuthProperties#configured()} 관례와
 * 동일하게, 미설정이면 등록 0건인 repository로 대체해 부팅에 영향이 없게 한다
 * (그 경우 {@code /oauth2/authorization/naver}는 단순히 "등록 없음"으로 처리된다).
 */
@Configuration
public class NaverClientRegistrationConfig {

  static final String REGISTRATION_ID = "naver";

  @Bean
  public ClientRegistrationRepository clientRegistrationRepository(NaverOAuthProperties props) {
    if (!props.configured()) {
      return registrationId -> null;
    }
    return new InMemoryClientRegistrationRepository(naverRegistration(props));
  }

  private ClientRegistration naverRegistration(NaverOAuthProperties props) {
    return ClientRegistration.withRegistrationId(REGISTRATION_ID)
        .clientId(props.clientId())
        .clientSecret(props.clientSecret())
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
        .tokenUri("https://nid.naver.com/oauth2.0/token")
        .userInfoUri("https://openapi.naver.com/v1/nid/me")
        .userNameAttributeName("response")
        .clientName("Naver")
        .build();
  }
}
