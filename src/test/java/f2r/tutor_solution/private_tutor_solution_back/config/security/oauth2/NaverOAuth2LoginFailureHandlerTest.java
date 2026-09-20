package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * state 불일치·인가 거부·token/profile 실패 등 모든 인증 실패가 하나의 사용자용 목적지로
 * 모이는지 검증한다. 원인 메시지 원문은 응답에 노출하지 않는다(로그에는 예외 클래스명만).
 */
class NaverOAuth2LoginFailureHandlerTest {

  private final NaverOAuth2LoginFailureHandler handler = new NaverOAuth2LoginFailureHandler();

  @Test
  void redirectsToNewStartFailureMarkerWithoutExposingReason() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationFailure(
        new MockHttpServletRequest(), response,
        new OAuth2AuthenticationException(new OAuth2Error("naver_profile_no_id"), "sensitive detail"));

    assertThat(response.getRedirectedUrl()).isEqualTo("/newstart?login=naver_failed");
    assertThat(response.getRedirectedUrl()).doesNotContain("sensitive detail");
  }
}
