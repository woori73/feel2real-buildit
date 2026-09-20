package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * NaverOAuth2LoginSuccessHandler given/when/then — NaverOAuth2UserService가 attribute에
 * 실어 둔 f2rUserId/f2rLoginId/f2rRole만으로 기존 JwtTokenProvider/CookieUtil을 호출해
 * /newstart로 리다이렉트하는지 검증한다(새 인증 로직 없음, 어댑터 역할만).
 */
class NaverOAuth2LoginSuccessHandlerTest {

  private final JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
  private final CookieUtil cookieUtil = Mockito.mock(CookieUtil.class);
  private final NaverOAuth2LoginSuccessHandler handler =
      new NaverOAuth2LoginSuccessHandler(tokenProvider, cookieUtil);

  private OAuth2User oauth2User(Long userId, String loginId, String role) {
    Map<String, Object> attrs = new java.util.LinkedHashMap<>();
    attrs.put("id", "375412389021");
    if (userId != null) attrs.put("f2rUserId", userId);
    if (loginId != null) attrs.put("f2rLoginId", loginId);
    if (role != null) attrs.put("f2rRole", role);
    return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")), attrs, "id");
  }

  @Test
  void issuesExistingJwtCookieAndRedirectsToNewStart() throws Exception {
    Authentication auth = new UsernamePasswordAuthenticationToken(
        oauth2User(42L, "naverstd_abcd", "STUDENT"), null, List.of());
    when(tokenProvider.issue(42L, "naverstd_abcd", "STUDENT")).thenReturn("jwt-token");

    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

    verify(tokenProvider).issue(42L, "naverstd_abcd", "STUDENT");
    verify(cookieUtil).writeAccessToken(eq(response), eq("jwt-token"));
    assertThat(response.getRedirectedUrl()).isEqualTo("/newstart");
  }

  @Test
  void missingLinkAttributesFailsSafeWithoutIssuingToken() throws Exception {
    Authentication auth = new UsernamePasswordAuthenticationToken(
        oauth2User(null, null, null), null, List.of());

    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

    Mockito.verifyNoInteractions(tokenProvider, cookieUtil);
    assertThat(response.getRedirectedUrl()).isEqualTo("/newstart?login=naver_failed");
  }
}
