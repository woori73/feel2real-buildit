package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * NaverOAuthStateService given/when/then — state 발급/검증(round-trip), 위조·불일치 거부,
 * returnUrl open-redirect 정제 규칙을 검증한다.
 */
@DisplayName("NaverOAuthStateService — state 서명/검증·returnUrl 정제")
class NaverOAuthStateServiceTest {

  private NaverOAuthStateService service;

  @BeforeEach
  void setUp() {
    JwtProperties jwt = new JwtProperties(
        "12345678901234567890123456789012", 86400, "ACCESS_TOKEN", false, "");
    service = new NaverOAuthStateService(jwt);
  }

  private Cookie stateCookie(MockHttpServletResponse response) {
    for (String header : response.getHeaders("Set-Cookie")) {
      if (header.startsWith(NaverOAuthStateService.COOKIE_NAME + "=")) {
        String value = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        return new Cookie(NaverOAuthStateService.COOKIE_NAME, value);
      }
    }
    throw new AssertionError("state cookie not written");
  }

  @Test
  void issuedStateRoundTripsToOriginalReturnUrl() {
    // given
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    String nonce = service.issue("/newstart/chat", response);
    MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
    callbackRequest.setCookies(stateCookie(response));

    // then
    assertThat(service.validate(callbackRequest, nonce)).contains("/newstart/chat");
  }

  @Test
  void tamperedCookiePayloadIsRejected() {
    // given
    MockHttpServletResponse response = new MockHttpServletResponse();
    String nonce = service.issue("/newstart", response);
    String original = stateCookie(response).getValue();
    String tampered = original.replace("0", "1");

    // when
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(NaverOAuthStateService.COOKIE_NAME, tampered));

    // then — 서명 실패 또는 payload 변조로 거부 (returnUrl을 /evil로 바꿔도 통과하면 안 됨)
    assertThat(service.validate(request, nonce)).isEmpty();
  }

  @Test
  void mismatchedStateIsRejected() {
    // given
    MockHttpServletResponse response = new MockHttpServletResponse();
    service.issue("/newstart", response);

    // when
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(stateCookie(response));

    // then — 남의 state(다른 nonce)로는 통과 불가
    assertThat(service.validate(request, "deadbeefdeadbeefdeadbeefdeadbeef")).isEmpty();
  }

  @Test
  void missingCookieOrStateIsRejected() {
    assertThat(service.validate(new MockHttpServletRequest(), "abc")).isEmpty();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(NaverOAuthStateService.COOKIE_NAME, "garbage"));
    assertThat(service.validate(request, "abc")).isEmpty();
  }

  @Test
  void rejectsOpenRedirectReturnUrls() {
    assertThat(service.sanitizeReturnUrl("https://evil.example.com")).isEqualTo("/newstart");
    assertThat(service.sanitizeReturnUrl("//evil.example.com")).isEqualTo("/newstart");
    assertThat(service.sanitizeReturnUrl("javascript:alert(1)")).isEqualTo("/newstart");
    assertThat(service.sanitizeReturnUrl("evil.com/path")).isEqualTo("/newstart");
    assertThat(service.sanitizeReturnUrl(null)).isEqualTo("/newstart");
    assertThat(service.sanitizeReturnUrl("")).isEqualTo("/newstart");
    assertThat(service.sanitizeReturnUrl("/a|b")).isEqualTo("/newstart");
  }

  @Test
  void acceptsInternalReturnUrls() {
    assertThat(service.sanitizeReturnUrl("/newstart")).isEqualTo("/newstart");
    assertThat(service.sanitizeReturnUrl("/newstart/chat?room=1")).isEqualTo("/newstart/chat?room=1");
    assertThat(service.sanitizeReturnUrl("\\newstart")).isEqualTo("/newstart");
  }

  @Test
  void clearWritesMaxAgeZeroCookie() {
    // given
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    service.clear(response);

    // then
    String header = response.getHeader("Set-Cookie");
    assertThat(header).contains(NaverOAuthStateService.COOKIE_NAME + "=")
        .contains("Max-Age=0");
  }
}
