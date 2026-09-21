package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
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
 * STEP 2 — {@code response.id}를 {@link NaverAccountResolutionService}로 기존/신규 Feel2Real
 * User에 연결하는 데까지만 한다(docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md
 * §PHASE 10). JWT 발급, 로그인 쿠키, {@code /newstart} redirect는 절대 하지 않는다 — STEP 3 범위.
 *
 * <p>{@code accountResolutionService}가 없는 극히 예외적인 컨텍스트(일부 슬라이스 테스트)를 대비해
 * null을 방어적으로 처리한다 — 운영(prd)과 전체 Spring 컨텍스트에서는 항상 존재한다.
 */
public class NaverOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(NaverOAuth2LoginSuccessHandler.class);

  private final NaverAccountResolutionService accountResolutionService;

  public NaverOAuth2LoginSuccessHandler(NaverAccountResolutionService accountResolutionService) {
    this.accountResolutionService = accountResolutionService;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
    Object idAttr = oauth2User.getAttribute(NaverProfileParser.ID_KEY);
    boolean providerIdPresent = idAttr != null && !String.valueOf(idAttr).isBlank();
    // 실제 providerUserId 값은 로그/응답 어디에도 남기지 않는다 — 존재 여부만.
    log.info("NAVER_OAUTH_SUCCESS providerIdPresent={}", providerIdPresent);

    if (accountResolutionService == null) {
      log.warn("NAVER_OAUTH_FAILURE stage=account_resolution_service_unavailable");
      writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "{\"provider\":\"NAVER\",\"accountResolved\":false,\"step\":2}");
      return;
    }

    boolean accountResolved;
    try {
      NaverProfileIdentity identity = NaverProfileIdentity.from(oauth2User);
      User user = accountResolutionService.resolveUser(identity);
      accountResolved = user != null;
    } catch (RuntimeException ex) {
      // 예외 상세(스택트레이스)는 서버 로그에만 남기고 응답에는 노출하지 않는다.
      log.warn("NAVER_OAUTH_FAILURE stage=account_resolution_failure", ex);
      writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "{\"provider\":\"NAVER\",\"accountResolved\":false,\"step\":2}");
      return;
    }

    // STEP 2 범위: JWT/쿠키/redirect 없음 — 계정 resolution 결과만 비식별로 확인한다.
    writeJson(response, HttpServletResponse.SC_OK,
        "{\"provider\":\"NAVER\",\"providerIdPresent\":" + providerIdPresent
            + ",\"accountResolved\":" + accountResolved + ",\"step\":2}");
  }

  private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(body);
  }
}
