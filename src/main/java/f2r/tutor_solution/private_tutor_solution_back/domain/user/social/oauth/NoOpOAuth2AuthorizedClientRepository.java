package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 네이버 access token을 어디에도 저장하지 않는다 — 로그인 시 {@code response.id}를 얻는 데만
 * 쓰고 버린다. Spring Security의 기본 {@code OAuth2AuthorizedClientRepository}(=
 * {@code HttpSessionOAuth2AuthorizedClientRepository})는 access token을 담은
 * {@link OAuth2AuthorizedClient}를 HttpSession에 저장하는데, 이 앱은 전역
 * {@code SessionCreationPolicy.STATELESS}(SecurityConfig)라 세션 자체를 쓰지 않는다.
 * 저장하지 않으면 (a) 이 목적만으로 JSESSIONID 쿠키가 새로 생기는 일이 없고, (b) access token이
 * 요청 처리가 끝난 뒤 단 한 곳에도 남지 않는다(브라우저 전달 자체를 하지 않을 뿐 아니라 서버측
 * 어디에도 보존하지 않음) — 이후 STEP에서도 네이버 API를 추가로 호출할 계획이 없으므로 authorized
 * client를 재사용할 필요가 없다.
 */
public class NoOpOAuth2AuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

  @Override
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      String clientRegistrationId, Authentication principal, HttpServletRequest request) {
    return null;
  }

  @Override
  public void saveAuthorizedClient(
      OAuth2AuthorizedClient authorizedClient,
      Authentication principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    // 의도적으로 아무것도 하지 않는다 — access token을 세션/쿠키/DB 어디에도 남기지 않는다.
  }

  @Override
  public void removeAuthorizedClient(
      String clientRegistrationId, Authentication principal, HttpServletRequest request, HttpServletResponse response) {
    // 저장한 적이 없으므로 지울 것도 없다.
  }
}
