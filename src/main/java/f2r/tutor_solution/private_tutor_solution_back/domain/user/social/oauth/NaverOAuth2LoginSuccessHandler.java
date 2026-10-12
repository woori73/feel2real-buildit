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
 * 복귀 URL(검증됨) 또는 {@link #NEWSTART_REDIRECT_PATH}로 redirect.
 *
 * <p>2026-10-12 변경: 로그인 전 접근한 페이지로 복귀 지원. {@code oauth_return_url} 쿠키에
 * 저장된 내부 URL이 있으면 검증 후 해당 페이지로 이동, 없으면 기존 기본 경로(/newstart) 유지.
 *
 * <p>보안 원칙:
 * <ul>
 *   <li>복귀 URL은 내부 경로만 허용 — 외부 URL은 차단 (open redirect 방지).
 *   <li>요청 파라미터/state로 목적지를 받지 않는다 — 쿠키에 저장된 값만 사용.
 *   <li>JWT는 절대 URL query parameter에 싣지 않는다 — {@link CookieUtil}이 발급하는 기존 운영
 *       쿠키 정책(HttpOnly/SameSite=Lax/운영 Secure/운영 Domain) 그대로 재사용한다.
 *   <li>네이버 access token은 이 핸들러에 도달하기 전에 이미 폐기된다(SecurityConfig가 연결하는
 *       {@link NoOpOAuth2AuthorizedClientRepository} 참고) — 여기서도 다루지 않는다.
 *   <li>실패 시에도 예외 상세를 응답에 노출하지 않는다.
 * </ul>
 */
public class NaverOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(NaverOAuth2LoginSuccessHandler.class);

  /** 복귀 URL이 없을 때의 기본 이동 경로. */
  static final String NEWSTART_REDIRECT_PATH = "/newstart";

  /** 로그인 전 페이지 복귀용 쿠키명 (프론트에서 로그인 시작 전 설정). */
  static final String RETURN_URL_COOKIE = "oauth_return_url";

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

    // 복귀 URL 처리: 쿠키에 저장된 내부 URL이 있으면 검증 후 이동
    String redirectPath = resolveReturnUrl(request);
    // 사용한 복귀 쿠키는 정리
    clearReturnUrlCookie(request, response);

    log.info("NAVER_OAUTH_LOGIN_COMPLETE redirectPath={}", redirectPath);
    response.sendRedirect(redirectPath);
  }

  /**
   * 로그인 전 저장된 복귀 URL을 검증하여 반환한다.
   * 내부 경로가 아니면 기본 경로를 반환 (open redirect 방지).
   */
  private String resolveReturnUrl(HttpServletRequest request) {
    jakarta.servlet.http.Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return NEWSTART_REDIRECT_PATH;
    }
    String returnUrl = null;
    for (jakarta.servlet.http.Cookie cookie : cookies) {
      if (RETURN_URL_COOKIE.equals(cookie.getName())) {
        returnUrl = cookie.getValue();
        break;
      }
    }
    if (returnUrl == null || returnUrl.isBlank()) {
      return NEWSTART_REDIRECT_PATH;
    }
    try {
      returnUrl = java.net.URLDecoder.decode(returnUrl, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      log.warn("NAVER_OAUTH_RETURN_URL_DECODE_FAILURE");
      return NEWSTART_REDIRECT_PATH;
    }
    if (isSafeInternalUrl(returnUrl)) {
      return returnUrl;
    }
    log.warn("NAVER_OAUTH_RETURN_URL_BLOCKED url={}", returnUrl);
    return NEWSTART_REDIRECT_PATH;
  }

  /**
   * 내부 URL 검증 — open redirect 방지.
   * "/" 로 시작하고 "//" 로 시작하지 않으며, 외부 스킴을 포함하지 않아야 한다.
   */
  private boolean isSafeInternalUrl(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    // 외부 스킴 차단
    String lower = url.toLowerCase();
    if (lower.startsWith("http://") || lower.startsWith("https://")
        || lower.startsWith("javascript:") || lower.startsWith("data:")) {
      return false;
    }
    // //evil.com 형태 차단
    if (url.startsWith("//")) {
      return false;
    }
    // 내부 절대 경로만 허용
    if (!url.startsWith("/")) {
      return false;
    }
    // 경로 탐색 차단
    if (url.contains("..")) {
      return false;
    }
    return true;
  }

  private void clearReturnUrlCookie(HttpServletRequest request, HttpServletResponse response) {
    jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(RETURN_URL_COOKIE, "");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    // Secure/Domain은 운영 환경에 맞게 CookieUtil 정책을 따름 (여기서는 최소 설정)
    response.addCookie(cookie);
  }

  private void writeJsonError(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"provider\":\"NAVER\",\"success\":false}");
  }
}
