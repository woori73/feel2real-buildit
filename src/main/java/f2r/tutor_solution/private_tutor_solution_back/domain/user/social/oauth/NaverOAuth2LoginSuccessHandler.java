package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
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
 * STEP 3 — {@code response.id} → {@link NaverAccountResolutionService}로 User resolve →
 * {@link JwtTokenProvider}로 발급 → 기존 {@link CookieUtil}로 인증 쿠키 발급 →
 * {@link #NEWSTART_REDIRECT_PATH}로 redirect(docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md
 * §PHASE 3의 STEP 3 목표).
 *
 * <p>보안 원칙:
 * <ul>
 *   <li>redirect 목적지는 고정 상수 하나뿐이다 — 요청 파라미터/state로 목적지를 받지 않는다
 *       (open redirect 원천 차단).
 *   <li>JWT는 절대 URL query parameter에 싣지 않는다 — {@link CookieUtil}이 발급하는 기존 운영
 *       쿠키 정책(HttpOnly/SameSite=Lax/운영 Secure/운영 Domain) 그대로 재사용한다.
 *   <li>네이버 access token은 이 핸들러에 도달하기 전에 이미 폐기된다(SecurityConfig가 연결하는
 *       {@link NoOpOAuth2AuthorizedClientRepository} 참고) — 여기서도 다루지 않는다.
 *   <li>실패 시에도 예외 상세를 응답에 노출하지 않는다.
 * </ul>
 */
public class NaverOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(NaverOAuth2LoginSuccessHandler.class);

  /** 고정 내부 경로. 요청/쿼리/state로 override 불가 — open redirect 방지. */
  static final String NEWSTART_REDIRECT_PATH = "/newstart";

  private final NaverAccountResolutionService accountResolutionService;
  private final JwtTokenProvider tokenProvider;
  private final CookieUtil cookieUtil;
  private final NewStartMemberProfileService memberProfileService;

  public NaverOAuth2LoginSuccessHandler(
      NaverAccountResolutionService accountResolutionService,
      JwtTokenProvider tokenProvider,
      CookieUtil cookieUtil,
      NewStartMemberProfileService memberProfileService) {
    this.accountResolutionService = accountResolutionService;
    this.tokenProvider = tokenProvider;
    this.cookieUtil = cookieUtil;
    this.memberProfileService = memberProfileService;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
    Object idAttr = oauth2User.getAttribute(NaverProfileParser.ID_KEY);
    boolean providerIdPresent = idAttr != null && !String.valueOf(idAttr).isBlank();
    // 실제 providerUserId/토큰 값은 로그 어디에도 남기지 않는다 — 존재 여부만.
    log.info("NAVER_OAUTH_SUCCESS providerIdPresent={}", providerIdPresent);

    if (accountResolutionService == null || tokenProvider == null || cookieUtil == null) {
      log.warn("NAVER_OAUTH_FAILURE stage=step3_dependency_unavailable");
      writeJsonError(response);
      return;
    }

    User user;
    try {
      NaverProfileIdentity identity = NaverProfileIdentity.from(oauth2User);
      user = accountResolutionService.resolveUser(identity);
      // NewStart 표시 별명 최초 1회 seed. 실명(identity.name())은 절대 넘기지 않는다 — NAVER
      // 프로필 nickname만 후보로 쓰고, 이미 row가 있으면(재로그인/이미 커스텀함) no-op이다.
      // 별명 seed 실패가 로그인 자체를 막으면 안 된다(§8) — 별도로 감싸 실패해도 계속 진행한다.
      if (memberProfileService != null) {
        try {
          memberProfileService.ensureSeeded(user.getUserId(), identity.nickname());
        } catch (RuntimeException seedEx) {
          log.warn("NAVER_OAUTH_NICKNAME_SEED_FAILURE", seedEx);
        }
      }
    } catch (RuntimeException ex) {
      // 예외 상세(스택트레이스)는 서버 로그에만 남기고 응답에는 노출하지 않는다.
      log.warn("NAVER_OAUTH_FAILURE stage=account_resolution_failure", ex);
      writeJsonError(response);
      return;
    }

    // JWT는 절대 URL에 싣지 않는다 — 기존 쿠키 발급 경로(CookieUtil)만 사용한다.
    String token = tokenProvider.issue(user.getUserId(), user.getLoginId(), user.getRole().name());
    cookieUtil.writeAccessToken(response, token);

    log.info("NAVER_OAUTH_LOGIN_COMPLETE");
    response.sendRedirect(NEWSTART_REDIRECT_PATH);
  }

  private void writeJsonError(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"provider\":\"NAVER\",\"success\":false}");
  }
}
