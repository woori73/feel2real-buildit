package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthClient.NaverProfile;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * /oauth/naver/start·callback 계약 (비로그인 접근 가능 = SecurityConfig anyRequest permitAll 확인,
 * state 없으면 token exchange 호출 금지, 성공 시 기존 ACCESS_TOKEN JWT 쿠키 발급).
 * NaverOAuthClient/NaverLoginService는 mock — 실제 네이버 호출 없음.
 */
@WebMvcTest(controllers = NaverOAuthController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    CookieUtil.class,
    JwtTokenProvider.class,
    NaverOAuthStateService.class,
    NaverOAuthControllerTest.TestConfig.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.naver.oauth.client-id=test-client-id",
    "app.naver.oauth.client-secret=test-client-secret"
})
class NaverOAuthControllerTest {

  @TestConfiguration
  @EnableConfigurationProperties(NaverOAuthProperties.class)
  static class TestConfig {
  }

  @Autowired MockMvc mockMvc;
  @MockitoBean NaverOAuthClient client;
  @MockitoBean NaverLoginService loginService;

  @Test
  void anonymousStartRedirectsToNaverAuthorizeAndWritesStateCookie() throws Exception {
    MvcResult started = mockMvc.perform(get("/oauth/naver/start").param("returnUrl", "/newstart/chat"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location",
            org.hamcrest.Matchers.startsWith("https://nid.naver.com/oauth2.0/authorize"
                + "?response_type=code&client_id=test-client-id&state=")))
        .andReturn();
    org.assertj.core.api.Assertions.assertThat(
            started.getResponse().getHeaders("Set-Cookie"))
        .anyMatch(h -> h.startsWith("NAVER_OAUTH_STATE=") && h.contains("HttpOnly"));
  }

  @Test
  void callbackWithoutValidStateNeverCallsNaver() throws Exception {
    mockMvc.perform(get("/oauth/naver/callback")
            .param("code", "attacker-code").param("state", "forged"))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .redirectedUrl("/newstart?login=naver_invalid_state"));
    verifyNoInteractions(client, loginService);
  }

  @Test
  void callbackAfterUserCancelReturnsGuide() throws Exception {
    mockMvc.perform(get("/oauth/naver/callback").param("error", "access_denied"))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .redirectedUrl("/newstart?login=naver_cancelled"));
    verifyNoInteractions(client, loginService);
  }

  @Test
  void successfulCallbackIssuesExistingJwtCookieAndRedirectsToReturnUrl() throws Exception {
    // given — 실제 start 호출로 발급된 state 쿠키 + nonce를 그대로 재사용 (서명 round-trip)
    MvcResult started = mockMvc.perform(get("/oauth/naver/start")
            .param("returnUrl", "/newstart/chat?tab=me"))
        .andExpect(status().is3xxRedirection())
        .andReturn();
    String location = started.getResponse().getHeader("Location");
    String state = extractParam(location, "state");
    String stateCookie = extractCookieValue(
        started.getResponse(), "NAVER_OAUTH_STATE");

    when(client.exchangeCode(eq("code-123"), eq(state), any()))
        .thenReturn(Optional.of("access-token"));
    when(client.fetchProfile("access-token"))
        .thenReturn(Optional.of(new NaverProfile("375412389021", "김철수", "cheolsu", null)));
    when(loginService.loginOrUpdate(any()))
        .thenReturn(new LoginResponse(42L, "naver_abcd", "김철수", "cheolsu", "cheolsu", "STUDENT"));

    // when
    MvcResult done = mockMvc.perform(get("/oauth/naver/callback")
            .param("code", "code-123").param("state", state)
            .cookie(new jakarta.servlet.http.Cookie("NAVER_OAUTH_STATE", stateCookie)))
        // then — returnUrl는 서명된 값만 사용된다 (query의 다른 값으로 덮어쓸 수 없음)
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .redirectedUrl("/newstart/chat?tab=me"))
        .andReturn();
    org.assertj.core.api.Assertions.assertThat(done.getResponse().getHeaders("Set-Cookie"))
        .anyMatch(h -> h.startsWith("ACCESS_TOKEN=") && h.contains("HttpOnly"));

    verify(client).exchangeCode(eq("code-123"), eq(state), any());
    verify(loginService).loginOrUpdate(any());
  }

  @Test
  void tokenExchangeFailureDoesNotCreateSession() throws Exception {
    MvcResult started = mockMvc.perform(get("/oauth/naver/start"))
        .andExpect(status().is3xxRedirection())
        .andReturn();
    String state = extractParam(started.getResponse().getHeader("Location"), "state");
    String stateCookie = extractCookieValue(started.getResponse(), "NAVER_OAUTH_STATE");
    when(client.exchangeCode(any(), any(), any())).thenReturn(Optional.empty());

    mockMvc.perform(get("/oauth/naver/callback")
            .param("code", "bad").param("state", state)
            .cookie(new jakarta.servlet.http.Cookie("NAVER_OAUTH_STATE", stateCookie)))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/newstart?login=naver_failed"));

    verifyNoInteractions(loginService);
  }

  private static String extractParam(String url, String name) {
    String query = url.substring(url.indexOf('?') + 1);
    for (String pair : query.split("&")) {
      int eqIndex = pair.indexOf('=');
      if (eqIndex > 0 && pair.substring(0, eqIndex).equals(name)) {
        return pair.substring(eqIndex + 1);
      }
    }
    throw new AssertionError("param not found: " + name + " in " + url);
  }

  private static String extractCookieValue(
      org.springframework.mock.web.MockHttpServletResponse response, String name) {
    for (String header : response.getHeaders("Set-Cookie")) {
      if (header.startsWith(name + "=")) {
        String value = header.substring(name.length() + 1);
        int semicolon = value.indexOf(';');
        return semicolon >= 0 ? value.substring(0, semicolon) : value;
      }
    }
    throw new AssertionError("cookie not found: " + name);
  }
}
