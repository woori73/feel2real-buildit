package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * STEP 1 전용 성공 핸들러 — OAuth2 라운드트립이 {@code response.id} 획득까지 완주했음만 확인한다.
 *
 * <p>docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md STEP 1 범위에 따라 User/
 * SocialAccount 생성, JWT 발급, 쿠키 발급을 절대 하지 않는다 — 전부 STEP 2/3에서 이 핸들러를
 * 교체하며 추가한다. 지금은 비식별 진단 응답만 돌려준다.
 */
public class NaverOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(NaverOAuth2LoginSuccessHandler.class);

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
    Object idAttr = oauth2User.getAttribute(NaverProfileParser.ID_KEY);
    boolean providerIdPresent = idAttr != null && !String.valueOf(idAttr).isBlank();

    // 실제 providerUserId 값은 로그/응답 어디에도 남기지 않는다 — 존재 여부만.
    log.info("NAVER_OAUTH_SUCCESS providerIdPresent={}", providerIdPresent);

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(
        "{\"provider\":\"NAVER\",\"providerIdPresent\":" + providerIdPresent + ",\"step\":1}");
  }
}
