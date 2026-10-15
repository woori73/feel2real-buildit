package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * ChartAgentTokenFilter 단위 검증 (무DB).
 *
 * <p>given/when/then: 유효 토큰+allowlist → chart-agent 인증 / 오토큰·무헤더·미설정·비allowlist →
 * 미인증 / 기존 인증 보존.
 */
class ChartAgentTokenFilterTest {

  private static final String TOKEN = "test-agent-token-123";

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    AuditorContextHolder.clear();
  }

  private MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
    req.setRequestURI(uri);
    return req;
  }

  @Test
  void givenValidTokenOnAllowlistedPost_whenFilter_thenAuthenticatesAsChartAgent() throws Exception {
    // given
    ChartAgentTokenFilter filter = new ChartAgentTokenFilter(TOKEN);
    MockHttpServletRequest req = request("POST", "/api/admin/chart/tools/research-drafts");
    req.addHeader("Authorization", "Bearer " + TOKEN);
    FilterChain chain = mock(FilterChain.class);

    // when
    filter.doFilter(req, new MockHttpServletResponse(), chain);

    // then
    verify(chain).doFilter(eq(req), any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(AuditorContextHolder.get()).isNull();
  }

  @Test
  void givenValidToken_whenFilter_thenSetsAuthenticationDuringChain() throws Exception {
    // given
    ChartAgentTokenFilter filter = new ChartAgentTokenFilter(TOKEN);
    MockHttpServletRequest req = request("GET", "/api/admin/chart/tools/context");
    req.addHeader("Authorization", "Bearer " + TOKEN);
    List<Object> seen = new java.util.ArrayList<>();
    FilterChain chain = (request, response) -> {
      seen.add(SecurityContextHolder.getContext().getAuthentication());
      seen.add(AuditorContextHolder.get());
    };

    // when
    filter.doFilter(req, new MockHttpServletResponse(), chain);

    // then
    assertThat(seen).hasSize(2);
    assertThat(seen.get(0)).isNotNull();
    assertThat(seen.get(0).toString()).contains("chart-agent");
    assertThat(seen.get(1)).isEqualTo("chart-agent");
  }

  @Test
  void givenWrongToken_whenFilter_thenNoAuthentication() throws Exception {
    // given
    ChartAgentTokenFilter filter = new ChartAgentTokenFilter(TOKEN);
    MockHttpServletRequest req = request("POST", "/api/admin/chart/tools/research-drafts");
    req.addHeader("Authorization", "Bearer wrong-token");
    List<Object> seen = new java.util.ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    // when
    filter.doFilter(req, new MockHttpServletResponse(), chain);

    // then
    assertThat(seen).containsExactly((Object) null);
  }

  @Test
  void givenNoHeader_whenFilter_thenNoAuthentication() throws Exception {
    // given
    ChartAgentTokenFilter filter = new ChartAgentTokenFilter(TOKEN);
    MockHttpServletRequest req = request("GET", "/api/admin/chart/tools/regions/next");
    List<Object> seen = new java.util.ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    // when
    filter.doFilter(req, new MockHttpServletResponse(), chain);

    // then
    assertThat(seen).containsExactly((Object) null);
  }

  @Test
  void givenBlankConfiguredToken_whenFilter_thenDisabled() throws Exception {
    // given
    ChartAgentTokenFilter filter = new ChartAgentTokenFilter("   ");
    MockHttpServletRequest req = request("POST", "/api/admin/chart/tools/research-drafts");
    req.addHeader("Authorization", "Bearer anything");
    List<Object> seen = new java.util.ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    // when
    filter.doFilter(req, new MockHttpServletResponse(), chain);

    // then
    assertThat(seen).containsExactly((Object) null);
  }

  @Test
  void givenValidTokenOnNonAllowlistedPath_whenFilter_thenNoAuthentication() throws Exception {
    // given — revert/rebuild/decision 은 Agent allowlist 밖 (ROLE_ADMIN 전용 유지)
    ChartAgentTokenFilter filter = new ChartAgentTokenFilter(TOKEN);
    String[] nonAllowlisted = {
        "/api/admin/chart/tools/review-batches/1/revert",
        "/api/admin/chart/tools/publications/rebuild",
        "/api/admin/chart/tools/review-batches/1/decision",
        "/api/admin/chart/tools/review-batches/1/finalize"
    };
    for (String uri : nonAllowlisted) {
      MockHttpServletRequest req = request("POST", uri);
      req.addHeader("Authorization", "Bearer " + TOKEN);
      List<Object> seen = new java.util.ArrayList<>();
      FilterChain chain = (request, response) ->
          seen.add(SecurityContextHolder.getContext().getAuthentication());

      // when
      filter.doFilter(req, new MockHttpServletResponse(), chain);

      // then
      assertThat(seen).as(uri).containsExactly((Object) null);
    }
  }

  @Test
  void givenExistingAuthentication_whenFilter_thenPreserved() throws Exception {
    // given — 앞단 JWT 필터의 ADMIN 인증을 덮어쓰거나 지우지 않는다
    ChartAgentTokenFilter filter = new ChartAgentTokenFilter(TOKEN);
    var existing = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
        "admin", null,
        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(existing);
    MockHttpServletRequest req = request("POST", "/api/admin/chart/tools/research-drafts");
    req.addHeader("Authorization", "Bearer " + TOKEN);
    List<Object> seen = new java.util.ArrayList<>();
    FilterChain chain = (request, response) ->
        seen.add(SecurityContextHolder.getContext().getAuthentication());

    // when
    filter.doFilter(req, new MockHttpServletResponse(), chain);

    // then
    assertThat(seen).containsExactly(existing);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existing);
  }
}
