package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * STEP 3 성공 핸들러 — JWT 발급/쿠키/redirect. 순수 POJO({@link JwtTokenProvider}/
 * {@link CookieUtil})만 있으면 되므로 Spring 컨텍스트 없이 단위테스트로 검증 가능하다.
 */
@DisplayName("NaverOAuth2LoginSuccessHandler (STEP 3)")
class NaverOAuth2LoginSuccessHandlerTest {

  private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
      "test-only-jwt-secret-32-bytes-minimum-xxxxx", 3600L, "ACCESS_TOKEN", false, null);

  private JwtTokenProvider tokenProvider;
  private CookieUtil cookieUtil;

  @BeforeEach
  void setUp() {
    tokenProvider = new JwtTokenProvider(JWT_PROPERTIES);
    cookieUtil = new CookieUtil(JWT_PROPERTIES);
  }

  private static Authentication naverAuthentication(String providerUserId) {
    OAuth2User oauth2User = new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("id", providerUserId), "id");
    return new UsernamePasswordAuthenticationToken(oauth2User, null, oauth2User.getAuthorities());
  }

  private static User user(long userId, Role role) {
    User user = User.builder()
        .loginId("naver_" + userId)
        .password("irrelevant-already-encoded")
        .name("네이버 회원")
        .nickname("naver_" + userId)
        .role(role)
        .build();
    org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", userId);
    return user;
  }

  @Test
  @DisplayName("resolve 성공 시 JWT 쿠키를 발급하고 고정 경로(/newstart)로만 redirect한다")
  void issuesCookieAndRedirectsToFixedPath() throws Exception {
    NaverAccountResolutionService resolutionService = mock(NaverAccountResolutionService.class);
    when(resolutionService.resolveUser(any())).thenReturn(user(42L, Role.STUDENT));
    NaverOAuth2LoginSuccessHandler handler =
        new NaverOAuth2LoginSuccessHandler(resolutionService, tokenProvider, cookieUtil);

    MockHttpServletRequest request = new MockHttpServletRequest();
    // open redirect 시도 — 핸들러가 이 값을 절대 읽지 않는지 확인한다.
    request.setParameter("redirect_uri", "https://evil.example.com");
    request.setParameter("return_to", "//evil.example.com");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(request, response, naverAuthentication("stable-id-1"));

    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getRedirectedUrl()).isEqualTo("/newstart");

    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie).contains("ACCESS_TOKEN=");
    assertThat(setCookie).containsIgnoringCase("HttpOnly");
    assertThat(setCookie).contains("SameSite=Lax");
    // JWT는 URL에 실리지 않는다 — redirect 목적지에 쿼리 파라미터가 전혀 없어야 한다.
    assertThat(response.getRedirectedUrl()).doesNotContain("?");
  }

  @Test
  @DisplayName("resolveUser가 예외를 던지면 500 JSON만 응답하고 쿠키/redirect가 없다")
  void doesNotIssueCookieWhenResolutionFails() throws Exception {
    NaverAccountResolutionService resolutionService = mock(NaverAccountResolutionService.class);
    when(resolutionService.resolveUser(any())).thenThrow(new IllegalStateException("boom"));
    NaverOAuth2LoginSuccessHandler handler =
        new NaverOAuth2LoginSuccessHandler(resolutionService, tokenProvider, cookieUtil);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(request, response, naverAuthentication("stable-id-2"));

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getRedirectedUrl()).isNull();
    assertThat(response.getHeader("Set-Cookie")).isNull();
    assertThat(response.getContentAsString()).doesNotContain("boom"); // 예외 상세 미노출
  }

  @Test
  @DisplayName("resolutionService가 없으면(방어적 null 처리) 500만 응답하고 쿠키/redirect가 없다")
  void handlesMissingResolutionServiceSafely() throws Exception {
    NaverOAuth2LoginSuccessHandler handler =
        new NaverOAuth2LoginSuccessHandler(null, tokenProvider, cookieUtil);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(request, response, naverAuthentication("stable-id-3"));

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getRedirectedUrl()).isNull();
    assertThat(response.getHeader("Set-Cookie")).isNull();
  }
}
