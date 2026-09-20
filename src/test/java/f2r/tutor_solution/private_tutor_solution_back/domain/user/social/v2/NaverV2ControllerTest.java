package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2Client.NaverV2Profile;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2LoginService.LoginResult;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * /auth/naver/v2/start·callback 계약 — 기존 {@code NaverOAuthControllerTest}(V1)와 같은 계약을
 * 독립된 경로/쿠키로 검증하고, 신규가입/재로그인 분기와 STEP 7 로그 마커를 함께 고정한다.
 * NaverV2Client/NaverV2LoginService는 mock — 실제 네이버 호출·DB 없음.
 */
@WebMvcTest(controllers = NaverV2Controller.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    CookieUtil.class,
    JwtTokenProvider.class,
    NaverV2StateService.class,
    NaverV2ControllerTest.TestConfig.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.naver.oauth.client-id=test-client-id",
    "app.naver.oauth.client-secret=test-client-secret"
})
@ExtendWith(OutputCaptureExtension.class)
class NaverV2ControllerTest {

  @TestConfiguration
  @EnableConfigurationProperties(NaverOAuthProperties.class)
  static class TestConfig {
  }

  @Autowired MockMvc mockMvc;
  @MockitoBean NaverV2Client client;
  @MockitoBean NaverV2LoginService loginService;

  @Test
  void anonymousStartRedirectsToNaverAuthorizeAndWritesV2StateCookie() throws Exception {
    MvcResult started = mockMvc.perform(get("/auth/naver/v2/start"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location",
            org.hamcrest.Matchers.startsWith("https://nid.naver.com/oauth2.0/authorize"
                + "?response_type=code&client_id=test-client-id&state=")))
        .andReturn();
    assertThat(started.getResponse().getHeaders("Set-Cookie"))
        .anyMatch(h -> h.startsWith("NAVER_V2_OAUTH_STATE=") && h.contains("HttpOnly"));
  }

  @Test
  void callbackWithoutValidStateNeverCallsNaver(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/auth/naver/v2/callback")
            .param("code", "attacker-code").param("state", "forged"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/newstart/chat?login=naver_failed"));
    verifyNoInteractions(client, loginService);
    assertThat(output.getOut()).contains("NAVER_V2_CALLBACK_RECEIVED");
    assertThat(output.getOut()).contains("NAVER_V2_STATE_FAILED");
    assertThat(output.getOut()).doesNotContain("attacker-code").doesNotContain("forged");
  }

  @Test
  void callbackWithErrorParamNeverCallsNaver(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/auth/naver/v2/callback").param("error", "access_denied"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/newstart/chat?login=naver_failed"));
    verifyNoInteractions(client, loginService);
    assertThat(output.getOut()).contains("NAVER_V2_CALLBACK_RECEIVED");
    assertThat(output.getOut()).contains("NAVER_V2_CALLBACK_ERROR");
  }

  @Test
  void callbackWithoutCodeNeverCallsNaver(CapturedOutput output) throws Exception {
    MvcResult started = mockMvc.perform(get("/auth/naver/v2/start")).andReturn();
    String state = extractParam(started.getResponse().getHeader("Location"), "state");
    String stateCookie = extractCookieValue(started.getResponse(), "NAVER_V2_OAUTH_STATE");

    mockMvc.perform(get("/auth/naver/v2/callback")
            .param("state", state)
            .cookie(new Cookie("NAVER_V2_OAUTH_STATE", stateCookie)))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/newstart/chat?login=naver_failed"));
    verifyNoInteractions(client, loginService);
    assertThat(output.getOut()).contains("NAVER_V2_CODE_MISSING");
  }

  @Test
  void successfulCallbackForNewUserCreatesAccountAndIssuesJwtCookie(CapturedOutput output) throws Exception {
    MvcResult started = mockMvc.perform(get("/auth/naver/v2/start")).andReturn();
    String location = started.getResponse().getHeader("Location");
    String state = extractParam(location, "state");
    String stateCookie = extractCookieValue(started.getResponse(), "NAVER_V2_OAUTH_STATE");

    when(client.exchangeCode(eq("code-123"), eq(state), any()))
        .thenReturn(Optional.of("access-token"));
    when(client.fetchProfile("access-token"))
        .thenReturn(Optional.of(new NaverV2Profile("375412389021", "김철수", "cheolsu", null)));
    when(loginService.loginOrRegister(any())).thenReturn(new LoginResult(
        new LoginResponse(42L, "naver2_abcd", "김철수", "cheolsu", "cheolsu", "STUDENT"), true));

    MvcResult done = mockMvc.perform(get("/auth/naver/v2/callback")
            .param("code", "code-123").param("state", state)
            .cookie(new Cookie("NAVER_V2_OAUTH_STATE", stateCookie)))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/newstart/chat"))
        .andReturn();
    assertThat(done.getResponse().getHeaders("Set-Cookie"))
        .anyMatch(h -> h.startsWith("ACCESS_TOKEN=") && h.contains("HttpOnly"));

    verify(client).exchangeCode(eq("code-123"), eq(state), any());
    verify(loginService).loginOrRegister(any());
    assertThat(output.getOut()).contains("NAVER_V2_CALLBACK_RECEIVED");
    assertThat(output.getOut()).contains("NAVER_V2_STATE_OK");
    assertThat(output.getOut()).contains("NAVER_V2_USER_CREATED");
    assertThat(output.getOut()).contains("NAVER_V2_SOCIAL_LINK_OK");
    assertThat(output.getOut()).contains("NAVER_V2_JWT_OK");
    assertThat(output.getOut()).contains("NAVER_V2_COOKIE_OK");
    assertThat(output.getOut()).contains("NAVER_V2_SUCCESS");
    assertThat(output.getOut()).doesNotContain("code-123").doesNotContain("access-token");
  }

  @Test
  void successfulCallbackForExistingUserLogsInWithoutCreating(CapturedOutput output) throws Exception {
    MvcResult started = mockMvc.perform(get("/auth/naver/v2/start")).andReturn();
    String state = extractParam(started.getResponse().getHeader("Location"), "state");
    String stateCookie = extractCookieValue(started.getResponse(), "NAVER_V2_OAUTH_STATE");

    when(client.exchangeCode(any(), any(), any())).thenReturn(Optional.of("access-token"));
    when(client.fetchProfile("access-token"))
        .thenReturn(Optional.of(new NaverV2Profile("375412389021", "김철수", "cheolsu", null)));
    when(loginService.loginOrRegister(any())).thenReturn(new LoginResult(
        new LoginResponse(1L, "naver2_x", "김철수", "cheolsu", "cheolsu", "STUDENT"), false));

    mockMvc.perform(get("/auth/naver/v2/callback")
            .param("code", "code-123").param("state", state)
            .cookie(new Cookie("NAVER_V2_OAUTH_STATE", stateCookie)))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/newstart/chat"));

    assertThat(output.getOut()).contains("NAVER_V2_USER_FOUND");
    assertThat(output.getOut()).doesNotContain("NAVER_V2_USER_CREATED");
  }

  @Test
  void tokenExchangeFailureDoesNotCreateSession(CapturedOutput output) throws Exception {
    MvcResult started = mockMvc.perform(get("/auth/naver/v2/start")).andReturn();
    String state = extractParam(started.getResponse().getHeader("Location"), "state");
    String stateCookie = extractCookieValue(started.getResponse(), "NAVER_V2_OAUTH_STATE");
    when(client.exchangeCode(any(), any(), any())).thenReturn(Optional.empty());

    mockMvc.perform(get("/auth/naver/v2/callback")
            .param("code", "bad").param("state", state)
            .cookie(new Cookie("NAVER_V2_OAUTH_STATE", stateCookie)))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/newstart/chat?login=naver_failed"));

    verifyNoInteractions(loginService);
    assertThat(output.getOut()).contains("NAVER_V2_CALLBACK_RECEIVED");
    assertThat(output.getOut()).contains("NAVER_V2_STATE_OK");
    assertThat(output.getOut()).doesNotContain("NAVER_V2_SUCCESS");
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
