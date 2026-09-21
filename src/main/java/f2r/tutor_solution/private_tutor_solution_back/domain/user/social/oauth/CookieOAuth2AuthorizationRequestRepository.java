package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;

/**
 * {@link OAuth2AuthorizationRequest}(state/PKCE 포함)를 HttpSession이 아닌 단명 쿠키에 저장한다.
 *
 * <p>Feel2Real 전체는 {@code SessionCreationPolicy.STATELESS}(SecurityConfig)다. Spring Security의
 * 기본 구현({@code HttpSessionOAuth2AuthorizationRequestRepository})은 HttpSession을 요구하는데,
 * 이는 이 프로젝트가 의도적으로 배제한 서버 세션을 다시 끌어들이는 것이다. 대신 OAuth2 인가
 * 왕복(수십 초~수 분)에만 한정된 별도의 단명 쿠키를 발급해 STATELESS 원칙을 깨지 않는다.
 *
 * <p>이 쿠키는 {@link f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil}이
 * 발급하는 로그인 유지용 ACCESS_TOKEN 쿠키와 이름·용도·수명이 완전히 다르다 — 인가 왕복이 끝나면
 * (성공/실패 무관) 즉시 삭제된다.
 */
public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  private static final Logger log = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);

  static final String COOKIE_NAME = "naver_oauth2_auth_request";
  private static final int MAX_AGE_SECONDS = 180; // 인가 왕복 용도. 장기 보관 아님.

  private final boolean secureCookie;

  public CookieOAuth2AuthorizationRequestRepository(boolean secureCookie) {
    this.secureCookie = secureCookie;
  }

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return readCookie(request).map(this::deserialize).orElse(null);
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorizationRequest == null) {
      removeCookie(response);
      return;
    }
    log.info("NAVER_OAUTH_START");
    writeCookie(response, serialize(authorizationRequest), MAX_AGE_SECONDS);
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    log.info("NAVER_OAUTH_CALLBACK");
    OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    removeCookie(response);
    return authorizationRequest;
  }

  private void removeCookie(HttpServletResponse response) {
    writeCookie(response, "", 0);
  }

  private void writeCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
    ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAgeSeconds)
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private Optional<Cookie> readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
        return Optional.of(cookie);
      }
    }
    return Optional.empty();
  }

  private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
    return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(authorizationRequest));
  }

  private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(cookie.getValue());
      return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(decoded);
    } catch (RuntimeException ex) {
      // 위변조/만료된 쿠키 — 그냥 새 인가 요청으로 취급한다(예외 전파 금지, DoS 표면 최소화).
      log.warn("NAVER_OAUTH_FAILURE stage=auth_request_cookie_invalid");
      return null;
    }
  }
}
