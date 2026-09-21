package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 공식 Spring Security OAuth2 Client 로그인 성공 이후, 이 앱이 실제로 쓰는 인증 수단(기존
 * {@code ACCESS_TOKEN} JWT 쿠키)으로 넘기는 어댑터. {@link NaverOAuth2UserService}가 이미
 * User/SocialAccount 연결을 끝내고 그 결과(f2rUserId/f2rLoginId/f2rRole)를
 * {@link OAuth2User}의 attribute로 실어 뒀으므로, 여기서는 기존
 * {@link JwtTokenProvider}/{@link CookieUtil}만 그대로 호출한다 — 새 인증 로직·새 쿠키 계약
 * 없음(같은 bean, 같은 이름/Path/Domain/Secure/HttpOnly/SameSite — 일반 ID/PW 로그인과 완전 동일).
 *
 * <p>2026-09-21 실사용자 검증: {@code NAVER_OAUTH2_LOGIN_SUCCESS} 로그가 찍혔는데도 브라우저가
 * 로그인 상태로 반영되지 않는 사례가 보고됐다("로그가 성공을 말한다"≠"쿠키가 실제로 응답에 실렸다").
 * 그래서 이 로그 한 줄만 믿지 않고, 실제로 {@code Set-Cookie} 헤더가 응답에 붙었는지까지 이
 * 메서드 안에서 직접 재확인해 둘을 구분되는 로그로 남긴다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NaverOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final JwtTokenProvider tokenProvider;
  private final CookieUtil cookieUtil;
  private final JwtProperties jwtProperties;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    if (response.isCommitted()) {
      // 이 시점에 이미 커밋됐다면 아래 cookieUtil.writeAccessToken()의 Set-Cookie는 서블릿
      // 스펙상 조용히 무시된다 — 예외 없이 "성공"처럼 보이는 채로 쿠키만 안 실리는 정확히 그
      // 실패 모드를 잡기 위한 사전 체크다.
      log.warn("NAVER_OAUTH2_LOGIN_SUCCESS_RESPONSE_ALREADY_COMMITTED");
      return;
    }
    OAuth2User user = (OAuth2User) authentication.getPrincipal();
    Long userId = user.getAttribute("f2rUserId");
    String loginId = user.getAttribute("f2rLoginId");
    String role = user.getAttribute("f2rRole");
    if (userId == null || loginId == null || role == null) {
      // NaverOAuth2UserService가 항상 세 값을 채우므로 정상 흐름에서는 발생하지 않는다 —
      // 방어적 처리일 뿐, 실제 원인은 NAVER_OAUTH2_USER_* 로그로 추적한다.
      log.warn("NAVER_OAUTH2_LOGIN_SUCCESS_MISSING_LINK");
      response.sendRedirect("/newstart?login=naver_failed");
      return;
    }
    String token = tokenProvider.issue(userId, loginId, role);
    cookieUtil.writeAccessToken(response, token);

    boolean cookieHeaderPresent = response.getHeaders(HttpHeaders.SET_COOKIE).stream()
        .anyMatch(header -> header.startsWith(jwtProperties.cookieName() + "="));
    if (!cookieHeaderPresent) {
      // writeAccessToken() 호출은 끝났지만 실제 Set-Cookie 헤더가 응답에 없다 — 값은 노출하지
      // 않고 "실렸는지 여부"만 별도 마커로 구분한다. 이 로그가 찍힌다면 로그인 성공 판정 자체를
      // 재검토해야 한다(다음 실사용자 시도에서 이 마커 유무로 §문제 A의 근본 원인을 좁힌다).
      log.warn("NAVER_OAUTH2_LOGIN_SUCCESS_COOKIE_HEADER_MISSING");
    }

    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    log.info("NAVER_OAUTH2_LOGIN_SUCCESS");
    response.sendRedirect("/newstart");
  }
}
