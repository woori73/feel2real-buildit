package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link NewstartAgentTokenFilter} 단위 테스트.
 *
 * <p>검증 범위: allowlist 경로(GET /api/muse/newstart/knowledge/search)에 대해서만
 * 토큰 일치 시 ROLE_NEWSTART_AGENT 인증이 설정되고, 그 외는 인증되지 않는다.
 * 토큰 미설정 시 필터는 완전히 비활성화된다 (fail-closed).
 */
class NewstartAgentTokenFilterTest {

  private static final String SEARCH_PATH = "/api/muse/newstart/knowledge/search";

  private MockMvc mockMvcWithToken(String token) {
    NewstartAgentTokenFilter filter = new NewstartAgentTokenFilter(token);
    return MockMvcBuilders.standaloneSetup(new ProbeController()).addFilter(filter).build();
  }

  @Test
  void search_withMatchingToken_isAuthenticated() throws Exception {
    MockMvc mockMvc = mockMvcWithToken("test-token-123");
    try {
      mockMvc.perform(get(SEARCH_PATH).header("Authorization", "Bearer test-token-123"))
          .andExpect(status().isOk());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void search_withWrongToken_isNotAuthenticated() throws Exception {
    MockMvc mockMvc = mockMvcWithToken("test-token-123");
    mockMvc.perform(get(SEARCH_PATH).header("Authorization", "Bearer wrong-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void search_withoutToken_isNotAuthenticated() throws Exception {
    MockMvc mockMvc = mockMvcWithToken("test-token-123");
    mockMvc.perform(get(SEARCH_PATH))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void search_whenFilterDisabled_isNotAuthenticated() throws Exception {
    // given: NEWSTART_AGENT_TOKEN 미설정 (빈 값)
    MockMvc mockMvc = mockMvcWithToken("");
    mockMvc.perform(get(SEARCH_PATH).header("Authorization", "Bearer anything"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void nonAllowlistedMethod_withMatchingToken_isNotAuthenticated() throws Exception {
    // given: allowlist 밖 메서드(POST) — 토큰이 맞아도 인증하지 않는다
    MockMvc mockMvc = mockMvcWithToken("test-token-123");
    mockMvc.perform(post(SEARCH_PATH)
            .header("Authorization", "Bearer test-token-123"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminPath_withMatchingToken_isNotAuthenticated() throws Exception {
    MockMvc mockMvc = mockMvcWithToken("test-token-123");
    mockMvc.perform(get("/api/admin/newstart/wiki")
            .header("Authorization", "Bearer test-token-123"))
        .andExpect(status().isUnauthorized());
  }

  @RestController
  static class ProbeController {
    @GetMapping(SEARCH_PATH)
    @ResponseBody
    public String search() {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      boolean authed = auth != null && auth.getAuthorities().stream()
          .anyMatch(a -> a.getAuthority().equals("ROLE_" + NewstartAgentTokenFilter.ROLE));
      if (!authed) {
        throw new NotAuthenticatedException();
      }
      return "ok";
    }

    @GetMapping("/api/admin/newstart/wiki")
    @ResponseBody
    public String admin() {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null) {
        throw new NotAuthenticatedException();
      }
      return "ok";
    }

    @PostMapping(SEARCH_PATH)
    @ResponseBody
    public String searchPost() {
      // POST는 allowlist 밖 — 필터가 인증을 설정하지 않아야 한다
      var auth = SecurityContextHolder.getContext().getAuthentication();
      boolean authed = auth != null && auth.getAuthorities().stream()
          .anyMatch(a -> a.getAuthority().equals("ROLE_" + NewstartAgentTokenFilter.ROLE));
      if (!authed) {
        throw new NotAuthenticatedException();
      }
      return "ok";
    }
  }

  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.UNAUTHORIZED)
  static class NotAuthenticatedException extends RuntimeException {}
}
