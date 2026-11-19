package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("PasswordChangeGate — 임시 비밀번호 로그인 토큰이 쓸 수 있는 경로")
class PasswordChangeGateTest {

  @ParameterizedTest(name = "{0} {1} → {2}")
  @CsvSource({
      "GET,/account/password-change,true",
      "POST,/api/auth/password-change/temporary,true",
      "POST,/api/auth/logout,true",
      "GET,/api/auth/me,true",
      "GET,/css/password_reset.css,true",
      "GET,/js/api.js,true",
      "GET,/img/F2R_logo_pure.svg,true",
      "GET,/favicon.ico,true",
      "GET,/manifest.json,true",
      "GET,/sw.js,true",
      "GET,/offline.html,true",
      "GET,/stdt_dashboard,false",
      "GET,/api/me,false",
      "PATCH,/api/me,false",
      "POST,/api/auth/login,false",
      "POST,/js/api.js,false",
      "GET,/account/password-change/x,false"
  })
  void isAllowed(String method, String path, boolean expected) {
    // given & when
    boolean allowed = PasswordChangeGate.isAllowed(method, path);

    // then
    assertThat(allowed).isEqualTo(expected);
  }

  @Test
  @DisplayName("given 화면 GET when 막으면 then 설정 화면으로 302")
  void block_page_redirects() throws Exception {
    // given
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/stdt_dashboard");
    MockHttpServletResponse res = new MockHttpServletResponse();

    // when
    PasswordChangeGate.block(req, res);

    // then
    assertThat(res.getStatus()).isEqualTo(302);
    assertThat(res.getRedirectedUrl()).isEqualTo("/account/password-change");
  }

  @Test
  @DisplayName("given API 요청 when 막으면 then 403 + 안내 헤더 + JSON 안내")
  void block_api_forbidden() throws Exception {
    // given
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/me");
    MockHttpServletResponse res = new MockHttpServletResponse();

    // when
    PasswordChangeGate.block(req, res);

    // then
    assertThat(res.getStatus()).isEqualTo(403);
    assertThat(res.getHeader(PasswordChangeGate.HEADER)).isEqualTo("true");
    assertThat(res.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED").contains("/account/password-change");
  }
}
