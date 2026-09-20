package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2Client.NaverV2Profile;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2LoginService.LoginResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2StateService.NaverV2AuthorizeParams;
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
 * NewStart 전용 네이버 로그인 V2 — 기존 {@code /oauth/naver/*}(V1)와 이름·경로·쿠키·클래스 전부
 * 독립적인 신규 진입점. V1은 손대지 않고 그대로 유지한다(#338 이후 원인 미확정 상태로 병행 운영).
 *
 * <ul>
 *   <li>{@code GET /auth/naver/v2/start} — state 발급 후 네이버 인가 화면으로 302.
 *   <li>{@code GET /auth/naver/v2/callback} — state 검증 → code/token/profile → User 식별/자동가입 →
 *       기존 {@code ACCESS_TOKEN} JWT 쿠키 발급 → {@code /newstart/chat} 302.
 * </ul>
 *
 * <p>인증 결과(JWT/쿠키)는 기존 ID/PW 로그인과 완전히 동일한 코드를 재사용한다
 * ({@link JwtTokenProvider}, {@link CookieUtil}). NewStart는 네이버 로그인만 존재하며 회원가입
 * 페이지로 보내지 않는다 — 최초 로그인 = 자동 가입.
 *
 * <p>각 경계마다 PII/secret 없는 stage 로그를 남긴다(client secret·access token·authorization
 * code·개인정보는 로그에 남기지 않는다) — V1에서 겪은 "콜백이 도달했는지조차 구분 불가" 문제를
 * 처음부터 방지한다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class NaverV2Controller {

  private static final String CALLBACK_PATH = "/auth/naver/v2/callback";
  private static final String FAILURE_REDIRECT = "/newstart/chat?login=naver_failed";

  private final NaverOAuthProperties props; // client_id/secret 설정 바인딩만 재사용 (외부 호출 로직은 V2 전용)
  private final NaverV2StateService stateService;
  private final NaverV2Client client;
  private final NaverV2LoginService loginService;
  private final JwtTokenProvider tokenProvider;
  private final CookieUtil cookieUtil;

  @GetMapping("/auth/naver/v2/start")
  public void start(
      @RequestParam(name = "returnUrl", required = false) String returnUrl,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    if (!props.configured()) {
      response.sendRedirect("/newstart/chat?login=naver_unconfigured");
      return;
    }
    log.info("NAVER_V2_START");
    String nonce = stateService.issue(stateService.sanitizeReturnUrl(returnUrl), response);
    String authorizeUrl = stateService.authorizationUrl(
        new NaverV2AuthorizeParams(props.clientId(), resolveRedirectUri(request), nonce));
    response.sendRedirect(authorizeUrl);
  }

  @GetMapping("/auth/naver/v2/callback")
  public void callback(
      @RequestParam(name = "code", required = false) String code,
      @RequestParam(name = "state", required = false) String state,
      @RequestParam(name = "error", required = false) String error,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    log.info("NAVER_V2_CALLBACK_RECEIVED");
    try {
      if (error != null) {
        log.warn("NAVER_V2_CALLBACK_ERROR");
        response.sendRedirect(FAILURE_REDIRECT);
        return;
      }
      if (code == null || code.isBlank()) {
        log.warn("NAVER_V2_CODE_MISSING");
        response.sendRedirect(FAILURE_REDIRECT);
        return;
      }
      Optional<String> returnUrl = stateService.validate(request, state);
      if (returnUrl.isEmpty()) {
        log.warn("NAVER_V2_STATE_FAILED");
        response.sendRedirect(FAILURE_REDIRECT);
        return;
      }
      log.info("NAVER_V2_STATE_OK");
      String target = returnUrl.get();

      Optional<String> accessToken = client.exchangeCode(code, state, resolveRedirectUri(request));
      if (accessToken.isEmpty()) {
        response.sendRedirect(FAILURE_REDIRECT);
        return;
      }
      Optional<NaverV2Profile> profile = client.fetchProfile(accessToken.get());
      if (profile.isEmpty()) {
        response.sendRedirect(FAILURE_REDIRECT);
        return;
      }

      LoginResult result = loginService.loginOrRegister(profile.get());
      log.info(result.created() ? "NAVER_V2_USER_CREATED" : "NAVER_V2_USER_FOUND");
      log.info("NAVER_V2_SOCIAL_LINK_OK");

      String token = tokenProvider.issue(
          result.user().userId(), result.user().loginId(), result.user().role());
      log.info("NAVER_V2_JWT_OK");
      cookieUtil.writeAccessToken(response, token);
      log.info("NAVER_V2_COOKIE_OK");
      response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
      log.info("NAVER_V2_SUCCESS");
      response.sendRedirect(target);
    } catch (RuntimeException e) {
      // 회원 식별/생성·social 연결 단계 실패 — 예외 세부는 억제하고 stage만 남긴다(개인정보/토큰 노출 금지).
      log.warn("NAVER_V2_USER_FAILED reason={}", e.getClass().getSimpleName());
      if (!response.isCommitted()) {
        response.sendRedirect(FAILURE_REDIRECT);
      }
    } finally {
      stateService.clear(response);
    }
  }

  /**
   * 단일 source of truth — start/callback 양쪽에서 이 메서드만 사용한다(별도 env var 없이 요청
   * 기준 동적 산출; prd는 {@code server.forward-headers-strategy: framework}로 ALB의
   * X-Forwarded-Proto를 신뢰하므로 scheme이 https로 정확히 해석된다 — V1의 동일 패턴으로 이미
   * production에서 실측 검증됨).
   */
  private String resolveRedirectUri(HttpServletRequest request) {
    String scheme = request.getScheme();
    int port = request.getServerPort();
    boolean defaultPort = ("http".equals(scheme) && port == 80)
        || ("https".equals(scheme) && port == 443);
    String base = scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    return base + CALLBACK_PATH;
  }
}
