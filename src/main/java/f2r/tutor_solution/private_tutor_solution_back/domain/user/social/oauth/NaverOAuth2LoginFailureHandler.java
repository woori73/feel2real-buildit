package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * 실패 단계를 구분 가능한 marker로 남긴다("네이버 로그인에 실패했습니다"만 남고 원인 불명이던
 * 과거 문제를 반복하지 않기 위함). Client Secret/토큰/authorization code/profile 원문은 어떤
 * 경우에도 로그에 남기지 않는다 — Spring Security 표준 에러 코드 또는 이 구현이 던진 코드만 사용.
 */
public class NaverOAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  private static final Logger log = LoggerFactory.getLogger(NaverOAuth2LoginFailureHandler.class);

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    String stage = resolveStage(exception);
    log.warn("NAVER_OAUTH_FAILURE stage={}", stage);

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"provider\":\"NAVER\",\"success\":false}");
  }

  /**
   * Spring Security 내부 OAuth2 에러 코드 문자열은 버전에 따라 달라질 수 있어 알려진 코드만
   * 사람이 읽을 수 있는 stage로 매핑하고, 모르는 코드는 원문 코드를 그대로 붙여 진단 가능하게 한다
   * (추측으로 잘못된 stage를 붙이지 않는다).
   */
  String resolveStage(AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauthEx) {
      String code = oauthEx.getError().getErrorCode();
      return switch (code) {
        case NaverOAuthErrorCodes.MISSING_RESPONSE -> "missing_response";
        case NaverOAuthErrorCodes.MISSING_RESPONSE_ID -> "missing_response_id";
        case "invalid_token_response" -> "token_exchange_failure";
        case "invalid_user_info_response" -> "userinfo_failure";
        case "authorization_request_not_found" -> "callback_state_failure";
        case "invalid_state_parameter" -> "callback_state_failure";
        case "access_denied" -> "authorization_rejected";
        default -> "oauth_failure:" + code;
      };
    }
    return "unknown:" + exception.getClass().getSimpleName();
  }
}
