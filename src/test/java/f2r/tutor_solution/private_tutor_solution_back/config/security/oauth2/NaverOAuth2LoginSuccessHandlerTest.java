package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * /newstart로 리다이렉트하는지, 그리고 그 결과 쿠키가 일반 ID/PW 로그인과 정확히 같은
 * name/Path/Domain/Secure/HttpOnly/SameSite 계약을 갖는지 검증한다.
 *
 * <p>CookieUtil/JwtTokenProvider를 mock이 아닌 실제 인스턴스로 써서(둘 다 생성자 하나짜리
 * 단순 POJO) "로그가 성공을 말한다"와 "Set-Cookie 헤더가 실제로 응답에 실렸다"를 분리해
 * 검증한다 — 2026-09-21 실사용자 검증에서 나온 정확히 그 우려를 테스트로 고정한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class NaverOAuth2LoginSuccessHandlerTest {

  private JwtProperties jwtProperties;
  private JwtTokenProvider tokenProvider;
  private CookieUtil cookieUtil;
  private NaverOAuth2LoginSuccessHandler handler;

  @BeforeEach
  void setUp() {
    // 운영과 동일한 형태(prd: secureCookie=true, cookieDomain=.feel2real.co.kr)로 구성해
    // "일반 로그인과 동일 계약"을 실제로 비교할 수 있게 한다.
    jwtProperties = new JwtProperties(
        "12345678901234567890123456789012", 86400, "ACCESS_TOKEN", true, ".feel2real.co.kr");
    tokenProvider = new JwtTokenProvider(jwtProperties);
    cookieUtil = new CookieUtil(jwtProperties);
    handler = new NaverOAuth2LoginSuccessHandler(tokenProvider, cookieUtil, jwtProperties);
  }

  private OAuth2User oauth2User(Long userId, String loginId, String role) {
    Map<String, Object> attrs = new java.util.LinkedHashMap<>();
    attrs.put("id", "375412389021");
    if (userId != null) attrs.put("f2rUserId", userId);
    if (loginId != null) attrs.put("f2rLoginId", loginId);
    if (role != null) attrs.put("f2rRole", role);
    return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")), attrs, "id");
  }

  private String setCookieHeader(MockHttpServletResponse response) {
    return response.getHeaders("Set-Cookie").stream()
        .filter(h -> h.startsWith("ACCESS_TOKEN="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("ACCESS_TOKEN Set-Cookie header not found"));
  }

  @Test
  @DisplayName("일반 ID/PW 로그인(CookieUtil.writeAccessToken)과 정확히 동일한 쿠키 계약을 쓴다")
  void issuedCookieMatchesExistingLoginContractExactly() throws Exception {
    Authentication auth = new UsernamePasswordAuthenticationToken(
        oauth2User(42L, "naverstd_abcd", "STUDENT"), null, List.of());

    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

    // 별도 인증 체계가 아니라 CookieUtil을 직접 호출한 것과 100% 동일한 헤더가 나오는지 대조군으로 비교
    MockHttpServletResponse reference = new MockHttpServletResponse();
    cookieUtil.writeAccessToken(reference, "reference-token");

    String actual = setCookieHeader(response);
    String expected = setCookieHeader(reference);
    for (String attribute : new String[] {"Path=/", "Domain=.feel2real.co.kr", "Secure", "HttpOnly", "SameSite=Lax"}) {
      assertThat(actual).contains(attribute);
      assertThat(expected).contains(attribute);
    }
    assertThat(response.getRedirectedUrl()).isEqualTo("/newstart");
  }

  @Test
  @DisplayName("Set-Cookie 헤더가 실제로 응답에 실렸는지까지 확인한다 — LOGIN_SUCCESS 로그만 믿지 않는다")
  void logSuccessOnlyWhenCookieHeaderActuallyPresent(CapturedOutput output) throws Exception {
    Authentication auth = new UsernamePasswordAuthenticationToken(
        oauth2User(42L, "naverstd_abcd", "STUDENT"), null, List.of());

    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

    assertThat(setCookieHeader(response)).startsWith("ACCESS_TOKEN=");
    assertThat(output.getOut()).contains("NAVER_OAUTH2_LOGIN_SUCCESS");
    assertThat(output.getOut()).doesNotContain("NAVER_OAUTH2_LOGIN_SUCCESS_COOKIE_HEADER_MISSING");
    assertThat(output.getOut()).doesNotContain("NAVER_OAUTH2_LOGIN_SUCCESS_RESPONSE_ALREADY_COMMITTED");
  }

  @Test
  @DisplayName("응답이 이미 커밋된 뒤라면 쿠키를 쓰려 하지 않고 별도 마커로 남긴다")
  void alreadyCommittedResponseIsDetectedAndLogged(CapturedOutput output) throws Exception {
    Authentication auth = new UsernamePasswordAuthenticationToken(
        oauth2User(42L, "naverstd_abcd", "STUDENT"), null, List.of());

    MockHttpServletResponse response = new MockHttpServletResponse();
    response.getWriter().write("already flushed");
    response.flushBuffer(); // isCommitted() == true 재현

    handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

    assertThat(output.getOut()).contains("NAVER_OAUTH2_LOGIN_SUCCESS_RESPONSE_ALREADY_COMMITTED");
    assertThat(output.getOut()).doesNotContain("NAVER_OAUTH2_LOGIN_SUCCESS\n");
  }

  @Test
  void missingLinkAttributesFailsSafeWithoutIssuingToken() throws Exception {
    Authentication auth = new UsernamePasswordAuthenticationToken(
        oauth2User(null, null, null), null, List.of());

    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

    assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    assertThat(response.getRedirectedUrl()).isEqualTo("/newstart?login=naver_failed");
  }
}
