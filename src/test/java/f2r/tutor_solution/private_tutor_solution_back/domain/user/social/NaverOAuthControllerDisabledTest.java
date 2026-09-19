package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthStateService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * NAVER env 미설정(CI/로컬 기본) 상태 계약 — 부팅/화면은 정상이고 start만 안내 redirect한다.
 */
class NaverOAuthControllerDisabledTest {

  @Test
  void unconfiguredStartRedirectsWithGuideInsteadOfFailing() throws Exception {
    // given
    JwtProperties jwt = new JwtProperties(
        "12345678901234567890123456789012", 86400, "ACCESS_TOKEN", false, "");
    NaverOAuthProperties disabled = new NaverOAuthProperties("", null, "");
    NaverOAuthController controller = new NaverOAuthController(
        disabled, new NaverOAuthStateService(jwt),
        mock(NaverOAuthClient.class), mock(NaverLoginService.class),
        new JwtTokenProvider(jwt), new CookieUtil(jwt));
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    controller.start("/newstart", new MockHttpServletRequest(), response);

    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/newstart?login=naver_unconfigured");
  }
}
