package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
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
 * {@link JwtTokenProvider}/{@link CookieUtil}만 그대로 호출한다 — 새 인증 로직 없음.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NaverOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final JwtTokenProvider tokenProvider;
  private final CookieUtil cookieUtil;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
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
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    log.info("NAVER_OAUTH2_LOGIN_SUCCESS");
    response.sendRedirect("/newstart");
  }
}
