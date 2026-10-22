package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller.AdmissionsRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

class McpClientAddressTest {
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "10.20.0.10|192.0.2.99, 198.51.100.42|198.51.100.42",
        "10.20.0.10|192.0.2.98, 198.51.100.42|198.51.100.42",
        "10.20.0.10|192.0.2.99, 198.51.100.42:12345|198.51.100.42",
        "10.20.0.10|192.0.2.99, 198.51.100.42, 10.20.0.11|198.51.100.42",
        "203.0.113.25|192.0.2.99, 198.51.100.42|203.0.113.25",
        "203.0.113.25|192.0.2.98, 198.51.100.42|203.0.113.25",
        "10.20.0.10|192.0.2.99, invalid|10.20.0.10"
      })
  void givenFrameworkForwarding_whenPeerAndXffAreValidated_thenIgnoreForgedLeftAddress(
      String peer, String forwarded, String expected) throws Exception {
    var limiter = new AdmissionsRateLimiter(10, 60, 100, "10.20.0.0/24", 500);
    var request = new MockHttpServletRequest("POST", "/mcp/oauth/register");
    request.setRemoteAddr(peer);
    request.addHeader("X-Forwarded-For", forwarded);
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "synthetic.invalid");
    new ForwardedHeaderFilter()
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (wrapped, response) ->
                assertEquals(
                    expected, McpClientAddress.resolve((HttpServletRequest) wrapped, limiter)));
  }

  @org.junit.jupiter.api.Test
  void givenFrameworkForwardingWithoutValidTrust_whenMcpEnabled_thenFailClosed() {
    assertThrows(
        Exception.class,
        () -> McpTransportSecurity.requireProxyConfiguration("framework", "", java.util.List.of()));
    assertThrows(
        Exception.class,
        () ->
            McpTransportSecurity.requireProxyConfiguration(
                "framework", "0.0.0.0/0", java.util.List.of("0.0.0.0/0")));
    assertDoesNotThrow(
        () ->
            McpTransportSecurity.requireProxyConfiguration(
                "framework", "10.20.0.0/24", java.util.List.of()));
    assertDoesNotThrow(
        () -> McpTransportSecurity.requireProxyConfiguration("none", "", java.util.List.of()));
  }
}
