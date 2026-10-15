package f2r.tutor_solution.private_tutor_solution_back.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

  private CookieUtil cookieUtil(String cookieDomain) {
    return new CookieUtil(new JwtProperties("secret", 3600L, "ACCESS_TOKEN", false, cookieDomain));
  }

  @Test
  void write_addsDomainWhenCookieDomainConfigured() {
    // given: 운영처럼 cookie-domain 설정
    CookieUtil cookieUtil = cookieUtil(".feel2real.co.kr");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    cookieUtil.writeAccessToken(response, "token-value");

    // then
    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).contains("Domain=.feel2real.co.kr");
  }

  @Test
  void write_omitsDomainWhenCookieDomainBlank() {
    // given: 로컬처럼 cookie-domain 공란 → host-only
    CookieUtil cookieUtil = cookieUtil("");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    cookieUtil.writeAccessToken(response, "token-value");

    // then: Domain 속성 자체가 없어야 한다(빈 Domain 헤더 금지)
    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).doesNotContain("Domain");
  }

  @Test
  void write_omitsDomainWhenCookieDomainNull() {
    // given: cookie-domain 미지정(null)
    CookieUtil cookieUtil = cookieUtil(null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    cookieUtil.writeAccessToken(response, "token-value");

    // then
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Domain");
  }

  @Test
  void clear_usesSameDomainAsWriteToAvoidZombieSession() {
    // given: 로그아웃 clear도 발급과 동일 domain을 써야 domain-scoped 쿠키가 지워진다
    CookieUtil cookieUtil = cookieUtil(".feel2real.co.kr");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    cookieUtil.clearAccessToken(response);

    // then
    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).contains("Domain=.feel2real.co.kr");
    assertThat(setCookie).contains("Max-Age=0");
  }

  @Test
  void clear_omitsDomainWhenBlank() {
    // given
    CookieUtil cookieUtil = cookieUtil("");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    cookieUtil.clearAccessToken(response);

    // then
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Domain");
  }
}
