package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * state 불일치, 네이버 인가 거부, token/profile 실패 등 Spring Security OAuth2 Client 표준
 * 흐름이 던지는 모든 인증 실패를 여기서 한곳으로 받는다. 상세 원인은 {@code NAVER_OAUTH2_*}
 * 로그(이 클래스·{@link NaverOAuth2UserService})로 구분하고, 사용자 화면은 기존 NewStart
 * 채팅 페이지의 {@code ?login=naver_failed} 안내 문구를 그대로 재사용한다(secret/token/code
 * 값은 로그에 남기지 않는다).
 */
@Component
@Slf4j
public class NaverOAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    log.warn("NAVER_OAUTH2_LOGIN_FAILED reason={}", exception.getClass().getSimpleName());
    response.sendRedirect("/newstart?login=naver_failed");
  }
}
