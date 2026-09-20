package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * NaverV2StateService given/when/then — V1({@code NaverOAuthStateServiceTest})과 동일한
 * 계약을 독립된 쿠키({@code NAVER_V2_OAUTH_STATE})/기본 returnUrl({@code /newstart/chat})로 검증한다.
 */
@DisplayName("NaverV2StateService — state 서명/검증·returnUrl 정제 (NewStart V2)")
class NaverV2StateServiceTest {

  private NaverV2StateService service;

  @BeforeEach
  void setUp() {
    JwtProperties jwt = new JwtProperties(
        "12345678901234567890123456789012", 86400, "ACCESS_TOKEN", false, "");
    service = new NaverV2StateService(jwt);
  }

  private Cookie stateCookie(MockHttpServletResponse response) {
    for (String header : response.getHeaders("Set-Cookie")) {
      if (header.startsWith(NaverV2StateService.COOKIE_NAME + "=")) {
        String value = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        return new Cookie(NaverV2StateService.COOKIE_NAME, value);
      }
    }
    throw new AssertionError("state cookie not written");
  }

  @Test
  void issuedStateRoundTripsToOriginalReturnUrl() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    String nonce = service.issue("/newstart/chat", response);
    MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
    callbackRequest.setCookies(stateCookie(response));

    assertThat(service.validate(callbackRequest, nonce)).contains("/newstart/chat");
  }

  @Test
  void v1AndV2StateCookiesAreIndependent() {
    assertThat(NaverV2StateService.COOKIE_NAME).isNotEqualTo("NAVER_OAUTH_STATE");
    assertThat(NaverV2StateService.COOKIE_NAME).isEqualTo("NAVER_V2_OAUTH_STATE");
  }

  @Test
  void tamperedCookiePayloadIsRejected() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    String nonce = service.issue("/newstart/chat", response);
    String original = stateCookie(response).getValue();
    String tampered = original.replace("0", "1");

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(NaverV2StateService.COOKIE_NAME, tampered));

    assertThat(service.validate(request, nonce)).isEmpty();
  }

  @Test
  void mismatchedStateIsRejected() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    service.issue("/newstart/chat", response);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(stateCookie(response));

    assertThat(service.validate(request, "deadbeefdeadbeefdeadbeefdeadbeef")).isEmpty();
  }

  @Test
  void missingCookieOrStateIsRejected() {
    assertThat(service.validate(new MockHttpServletRequest(), "abc")).isEmpty();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(NaverV2StateService.COOKIE_NAME, "garbage"));
    assertThat(service.validate(request, "abc")).isEmpty();
  }

  @Test
  void rejectsOpenRedirectReturnUrls() {
    assertThat(service.sanitizeReturnUrl("https://evil.example.com")).isEqualTo("/newstart/chat");
    assertThat(service.sanitizeReturnUrl("//evil.example.com")).isEqualTo("/newstart/chat");
    assertThat(service.sanitizeReturnUrl("javascript:alert(1)")).isEqualTo("/newstart/chat");
    assertThat(service.sanitizeReturnUrl("evil.com/path")).isEqualTo("/newstart/chat");
    assertThat(service.sanitizeReturnUrl(null)).isEqualTo("/newstart/chat");
    assertThat(service.sanitizeReturnUrl("")).isEqualTo("/newstart/chat");
    assertThat(service.sanitizeReturnUrl("/a|b")).isEqualTo("/newstart/chat");
  }

  @Test
  void acceptsInternalReturnUrls() {
    assertThat(service.sanitizeReturnUrl("/newstart")).isEqualTo("/newstart");
    assertThat(service.sanitizeReturnUrl("/newstart/chat?room=1")).isEqualTo("/newstart/chat?room=1");
    assertThat(service.sanitizeReturnUrl("\\newstart")).isEqualTo("/newstart");
  }

  @Test
  void clearWritesMaxAgeZeroCookie() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    service.clear(response);

    String header = response.getHeader("Set-Cookie");
    assertThat(header).contains(NaverV2StateService.COOKIE_NAME + "=")
        .contains("Max-Age=0");
  }
}
