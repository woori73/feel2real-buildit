package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthClient.NaverProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 네이버 OAuth 2.0 로그인 진입점.
 *
 * <ul>
 *   <li>{@code GET /oauth/naver/start?returnUrl=...} — state 발급(서명 쿠키) 후 네이버 인가 화면으로 302.
 *   <li>{@code GET /oauth/naver/callback?code=...&state=...} — state 검증 → code/token/profile →
 *       Feel2Real User 식별/생성 → 기존 {@code ACCESS_TOKEN} JWT 쿠키 발급 → returnUrl 302.
 * </ul>
 *
 * <p>인증 상태는 기존 ID/PW 로그인과 완전히 동일하다(JwtTokenProvider + CookieUtil 재사용).
 * NewStart 화면뿐 아니라 사이트 전체가 동일 세션을 본다. 네이버 로그인은 STUDENT 회원만
 * 생성하며 ADMIN/TUTOR 권한을 부여하지 않는다. Client Secret은 서버 환경변수로만 주입되고,
 * 미설정 시 start는 안내와 함께 /newstart로 되돌아갈 뿐 앱 부팅에 영향이 없다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class NaverOAuthController {

  private final NaverOAuthProperties props;
  private final NaverOAuthStateService stateService;
  private final NaverOAuthClient client;
  private final NaverLoginService loginService;
  private final JwtTokenProvider tokenProvider;
  private final CookieUtil cookieUtil;

  @GetMapping("/oauth/naver/start")
  public void start(
      @RequestParam(name = "returnUrl", required = false) String returnUrl,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    if (!props.configured()) {
      response.sendRedirect("/newstart?login=naver_unconfigured");
      return;
    }
    String nonce = stateService.issue(stateService.sanitizeReturnUrl(returnUrl), response);
    response.sendRedirect(stateService.authorizationUrl(props, resolveRedirectUri(request), nonce));
  }

  @GetMapping("/oauth/naver/callback")
  public void callback(
      @RequestParam(name = "code", required = false) String code,
      @RequestParam(name = "state", required = false) String state,
      @RequestParam(name = "error", required = false) String error,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    // 2026-09-20 실기기 재조사: 이 진입 로그가 없으면 "콜백이 아예 안 왔다"와 "콜백은 왔는데
    // 아래 두 조용한 분기(취소/state 무효) 중 하나로 빠졌다"를 journal만으로 구분할 수 없었다.
    log.info("NAVER_CALLBACK_RECEIVED");
    String target = "/newstart";
    try {
      if (error != null || code == null || code.isBlank()) {
        log.warn("NAVER_CALLBACK_CANCELLED errorPresent={} codePresent={}",
            error != null, code != null && !code.isBlank());
        response.sendRedirect(target + "?login=naver_cancelled");
        return;
      }
      Optional<String> returnUrl = stateService.validate(request, state);
      if (returnUrl.isEmpty()) {
        log.warn("NAVER_CALLBACK_INVALID_STATE");
        response.sendRedirect(target + "?login=naver_invalid_state");
        return;
      }
      target = returnUrl.get();
      String redirectUri = resolveRedirectUri(request);
      Optional<String> accessToken = client.exchangeCode(code, state, redirectUri);
      if (accessToken.isEmpty()) {
        response.sendRedirect(target + "?login=naver_failed");
        return;
      }
      Optional<NaverProfile> profile = client.fetchProfile(accessToken.get());
      if (profile.isEmpty()) {
        response.sendRedirect(target + "?login=naver_failed");
        return;
      }
      LoginResponse user = loginService.loginOrUpdate(profile.get());
      String token = tokenProvider.issue(user.userId(), user.loginId(), user.role());
      cookieUtil.writeAccessToken(response, token);
      response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
      log.info("NAVER_CALLBACK_SUCCESS");
      response.sendRedirect(target);
    } catch (RuntimeException e) {
      // 회원 식별/생성·social 연결 단계 실패 — 예외 세부는 억제하고 stage만 남긴다(개인정보/토큰 노출 금지).
      log.warn("NAVER_SOCIAL_LOGIN_FAILED reason={}", e.getClass().getSimpleName());
      if (!response.isCommitted()) {
        response.sendRedirect(target + "?login=naver_failed");
      }
    } finally {
      stateService.clear(response);
    }
  }

  /** 설정값 우선, 비우면 요청 base URL로 산출(로컬 개발). 운영은 env 명시 등록이 원칙. */
  private String resolveRedirectUri(HttpServletRequest request) {
    if (props.redirectUri() != null && !props.redirectUri().isBlank()) {
      return props.redirectUri().trim();
    }
    String scheme = request.getScheme();
    int port = request.getServerPort();
    boolean defaultPort = ("http".equals(scheme) && port == 80)
        || ("https".equals(scheme) && port == 443);
    String base = scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    return base + "/oauth/naver/callback";
  }
}
