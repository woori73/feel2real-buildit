package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ArchivedRouteAccessFilterTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Given unmarked request, When access filter runs, Then it is a no-op")
  void unmarkedRequestPasses() throws Exception {
    // given
    AtomicBoolean invoked = new AtomicBoolean();

    // when
    new ArchivedRouteAccessFilter(new ArchivedRoutesProperties(true, true))
        .doFilter(request(false), new MockHttpServletResponse(),
            (wrapped, ignored) -> invoked.set(true));

    // then
    assertThat(invoked).isTrue();
  }

  @Test
  @DisplayName("Given anonymous archived request, When access filter runs, Then it returns 401")
  void anonymousArchivedRequestReturnsUnauthorized() throws Exception {
    // given
    SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
        "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    new ArchivedRouteAccessFilter(new ArchivedRoutesProperties(true, true))
        .doFilter(request(true), response, (wrapped, ignored) -> { });

    // then
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("Given non-admin archived request, When ADMIN is required, Then it returns 403")
  void nonAdminArchivedRequestReturnsForbidden() throws Exception {
    // given
    authenticate("ROLE_STUDENT");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    new ArchivedRouteAccessFilter(new ArchivedRoutesProperties(true, true))
        .doFilter(request(true), response, (wrapped, ignored) -> { });

    // then
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("Given admin archived request, When ADMIN is required, Then it proceeds")
  void adminArchivedRequestPasses() throws Exception {
    // given
    authenticate("ROLE_ADMIN");
    AtomicBoolean invoked = new AtomicBoolean();

    // when
    new ArchivedRouteAccessFilter(new ArchivedRoutesProperties(true, true))
        .doFilter(request(true), new MockHttpServletResponse(),
            (wrapped, ignored) -> invoked.set(true));

    // then
    assertThat(invoked).isTrue();
  }

  @Test
  @DisplayName("Given authenticated archived request, When ADMIN is optional, Then it proceeds")
  void authenticatedRequestPassesWhenAdminIsOptional() throws Exception {
    // given
    authenticate("ROLE_TUTOR");
    AtomicBoolean invoked = new AtomicBoolean();

    // when
    new ArchivedRouteAccessFilter(new ArchivedRoutesProperties(true, false))
        .doFilter(request(true), new MockHttpServletResponse(),
            (wrapped, ignored) -> invoked.set(true));

    // then
    assertThat(invoked).isTrue();
  }

  private MockHttpServletRequest request(boolean archived) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    if (archived) {
      request.setAttribute(ArchivedRouteAliasFilter.ARCHIVED_ATTRIBUTE, Boolean.TRUE);
    }
    return request;
  }

  private void authenticate(String authority) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "principal", null, List.of(new SimpleGrantedAuthority(authority))));
  }
}
