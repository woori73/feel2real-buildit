package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;

class ArchivedRouteAliasFilterTest {

  private final ArchivedRouteAliasFilter filter =
      new ArchivedRouteAliasFilter(new ArchivedRoutesProperties(true, true));

  @ParameterizedTest
  @ValueSource(strings = {"/archived", "/archived/"})
  @DisplayName("Given archived root, When alias filter runs, Then downstream sees /home")
  void archivedRootMapsToHome(String uri) throws Exception {
    // given
    MockHttpServletRequest request = request("GET", uri);
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, (wrapped, ignored) -> {
      // then
      assertThat(((MockHttpServletRequest) request).getDispatcherType().name()).isEqualTo("REQUEST");
      assertThat(((jakarta.servlet.http.HttpServletRequest) wrapped).getRequestURI()).isEqualTo("/home");
      assertThat(((jakarta.servlet.http.HttpServletRequest) wrapped).getServletPath()).isEqualTo("/home");
      assertThat(wrapped.getAttribute(ArchivedRouteAliasFilter.ARCHIVED_ATTRIBUTE)).isEqualTo(true);
    });

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("Given a context path and cached archived paths, When rewritten, Then both caches use target")
  void contextPathAndCachedPathsAreReplaced() throws Exception {
    // given
    MockHttpServletRequest request = request("POST", "/app/archived/api/items/one%20two");
    request.setContextPath("/app");
    request.setServletPath("/archived/api/items/one two");
    ServletRequestPathUtils.parseAndCache(request);
    UrlPathHelper.defaultInstance.resolveAndCacheLookupPath(request);

    // when & then
    filter.doFilter(request, new MockHttpServletResponse(), (wrapped, ignored) -> {
      var httpRequest = (jakarta.servlet.http.HttpServletRequest) wrapped;
      assertThat(httpRequest.getContextPath()).isEqualTo("/app");
      assertThat(httpRequest.getRequestURI()).isEqualTo("/app/api/items/one%20two");
      assertThat(httpRequest.getServletPath()).isEqualTo("/api/items/one%20two");
      assertThat(httpRequest.getRequestURL().toString())
          .isEqualTo("http://localhost/app/api/items/one%20two");
      assertThat(ServletRequestPathUtils.getParsedRequestPath(wrapped).pathWithinApplication().value())
          .isEqualTo("/api/items/one%20two");
      assertThat(UrlPathHelper.getResolvedLookupPath(wrapped)).isEqualTo("/api/items/one%20two");
    });
  }

  @Test
  @DisplayName("Given request metadata and body, When rewritten, Then only path getters change")
  void requestFidelityIsPreserved() throws Exception {
    // given
    MockHttpServletRequest request = request("PATCH", "/archived/api/items");
    request.setQueryString("page=2");
    request.addHeader("X-Probe", "kept");
    request.setCookies(new Cookie("probe", "cookie"));
    request.setContent("body".getBytes(StandardCharsets.UTF_8));

    // when & then
    filter.doFilter(request, new MockHttpServletResponse(), (wrapped, ignored) -> {
      var httpRequest = (jakarta.servlet.http.HttpServletRequest) wrapped;
      assertThat(httpRequest.getMethod()).isEqualTo("PATCH");
      assertThat(httpRequest.getQueryString()).isEqualTo("page=2");
      assertThat(httpRequest.getHeader("X-Probe")).isEqualTo("kept");
      assertThat(httpRequest.getCookies()[0].getValue()).isEqualTo("cookie");
      assertThat(httpRequest.getInputStream().readAllBytes())
          .isEqualTo("body".getBytes(StandardCharsets.UTF_8));
      assertThat(httpRequest.getAttribute(ArchivedRouteAliasFilter.ORIGINAL_URI_ATTRIBUTE))
          .isEqualTo("/archived/api/items");
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/archived/archived",
      "/archived/archived/api/items",
      "/archived/%61pi/items",
      "/archived/%2Fapi/items",
      "/archived/%5Capi/items",
      "/archived/%2e/items",
      "/archived/%2e%2e/items",
      "/archived/api;v=1/items",
      "/archived/api%3Bv=1/items",
      "/archived/%61pi;v=1/items",
      "/archived/%"
  })
  @DisplayName("Given ambiguous first segment, When filtered, Then request is rejected before chain")
  void ambiguousFirstSegmentIsRejected(String uri) throws Exception {
    // given
    AtomicBoolean invoked = new AtomicBoolean();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(request("POST", uri), response, (wrapped, ignored) -> invoked.set(true));

    // then
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(invoked).isFalse();
  }

  @Test
  @DisplayName("Given near prefix, When filtered, Then request remains untouched")
  void nearPrefixIsUntouched() throws Exception {
    // given
    MockHttpServletRequest request = request("GET", "/archived-preview/api/items");

    // when & then
    filter.doFilter(request, new MockHttpServletResponse(), (wrapped, ignored) -> {
      var httpRequest = (jakarta.servlet.http.HttpServletRequest) wrapped;
      assertThat(httpRequest.getRequestURI()).isEqualTo("/archived-preview/api/items");
      assertThat(httpRequest.getAttribute(ArchivedRouteAliasFilter.ARCHIVED_ATTRIBUTE)).isNull();
    });
  }

  @Test
  @DisplayName("Given FORWARD dispatch, When filter runs, Then alias is not applied")
  void forwardDispatchIsUntouched() throws Exception {
    // given
    MockHttpServletRequest request = request("GET", "/archived/api/items");
    request.setDispatcherType(DispatcherType.FORWARD);

    // when & then
    filter.doFilter(request, new MockHttpServletResponse(), (wrapped, ignored) -> {
      var httpRequest = (jakarta.servlet.http.HttpServletRequest) wrapped;
      assertThat(httpRequest.getRequestURI()).isEqualTo("/archived/api/items");
      assertThat(httpRequest.getAttribute(ArchivedRouteAliasFilter.ARCHIVED_ATTRIBUTE)).isNull();
    });
  }

  @Test
  @DisplayName("Given disabled alias, When exact archived path is requested, Then 404 wins")
  void disabledAliasReturnsNotFound() throws Exception {
    // given
    ArchivedRouteAliasFilter disabled =
        new ArchivedRouteAliasFilter(new ArchivedRoutesProperties(false, true));
    AtomicBoolean invoked = new AtomicBoolean();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    disabled.doFilter(request("GET", "/archived/home"), response,
        (wrapped, ignored) -> invoked.set(true));

    // then
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(invoked).isFalse();
  }

  private MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRequestURI(uri);
    request.setServletPath(uri);
    return request;
  }
}
