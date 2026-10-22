package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.Set;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class McpTransportSecurity {
  @Bean
  @Order(0)
  SecurityFilterChain mcpTransport(
      HttpSecurity http,
      McpOAuth oauth,
      McpRateLimit rate,
      @org.springframework.beans.factory.annotation.Value("${server.forward-headers-strategy:none}")
          String forwarding,
      @org.springframework.beans.factory.annotation.Value(
              "${app.admissions.rate-limit.trusted-proxies:}")
          String trustedProxies,
      f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller
              .AdmissionsRateLimiter
          addresses)
      throws Exception {
    http.securityMatcher(
            "/mcp",
            "/.well-known/oauth-protected-resource",
            "/.well-known/oauth-protected-resource/mcp",
            "/.well-known/oauth-authorization-server",
            "/mcp/oauth/register",
            "/mcp/oauth/token",
            "/mcp/oauth/revoke")
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                res.setHeader("Cache-Control", "no-store");
                res.setHeader("X-Content-Type-Options", "nosniff");
                try {
                  oauth.enabled();
                  requireProxyConfiguration(
                      forwarding, trustedProxies, addresses.invalidTrustedProxyEntries());
                  String origin = req.getHeader("Origin");
                  if (origin != null
                      && !Set.of(
                              oauth.base(),
                              "https://chatgpt.com",
                              "https://chat.openai.com",
                              "https://claude.ai")
                          .contains(origin)) throw McpOAuth.error(403, "invalid_origin");
                  if (origin != null) {
                    res.setHeader("Access-Control-Allow-Origin", origin);
                    res.setHeader("Vary", "Origin");
                    res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
                    res.setHeader(
                        "Access-Control-Allow-Headers",
                        "Authorization,Content-Type,Accept,MCP-Protocol-Version");
                    res.setHeader("Access-Control-Expose-Headers", "WWW-Authenticate");
                  }
                  if ("OPTIONS".equals(req.getMethod())) {
                    res.setStatus(204);
                    return;
                  }
                  if (req.getContentLengthLong() > 65536)
                    throw McpOAuth.error(413, "body_too_large");
                  String auth = req.getHeader("Authorization");
                  String clientAddress = McpClientAddress.resolve(req, addresses);
                  if ("/mcp".equals(req.getRequestURI()) && auth != null) {
                    try {
                      var identity = oauth.authenticate(auth);
                      rate.check("transport-user:" + identity.principal().userId(), 300);
                    } catch (org.springframework.web.server.ResponseStatusException e) {
                      rate.check("transport:" + clientAddress, 300);
                      throw e;
                    }
                  } else rate.check("transport:" + clientAddress, 300);
                  rate.check(
                      req.getRequestURI()
                          + ":"
                          + (auth == null || req.getRequestURI().endsWith("register")
                              ? clientAddress
                              : McpOAuth.hash(auth)),
                      req.getRequestURI().endsWith("register") ? 5 : 120);
                  // Limit chunked bodies too, before JSON/form deserialization.
                  var wrapped =
                      new HttpServletRequestWrapper(req) {
                        @Override
                        public ServletInputStream getInputStream() throws IOException {
                          var in = super.getInputStream();
                          return new ServletInputStream() {
                            int total;

                            public int read() throws IOException {
                              int b = in.read();
                              if (b != -1 && ++total > 65536)
                                throw new IOException("MCP body limit");
                              return b;
                            }

                            public int read(byte[] b, int off, int len) throws IOException {
                              int n = in.read(b, off, Math.min(len, 65537 - total));
                              if (n > 0 && (total += n) > 65536)
                                throw new IOException("MCP body limit");
                              return n;
                            }

                            public boolean isFinished() {
                              return in.isFinished();
                            }

                            public boolean isReady() {
                              return in.isReady();
                            }

                            public void setReadListener(ReadListener listener) {
                              in.setReadListener(listener);
                            }
                          };
                        }
                      };
                  chain.doFilter(wrapped, res);
                } catch (org.springframework.web.server.ResponseStatusException e) {
                  res.setStatus(e.getStatusCode().value());
                  if (e.getStatusCode().value() == 401)
                    res.setHeader(
                        "WWW-Authenticate",
                        "Bearer resource_metadata=\""
                            + oauth.base()
                            + "/.well-known/oauth-protected-resource/mcp\"");
                  res.setContentType("application/json");
                  if (e.getStatusCode().value() == 429) res.setHeader("Retry-After", "60");
                  res.getWriter().write("{\"error\":\"" + e.getReason() + "\"}");
                }
              }
            },
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  static void requireProxyConfiguration(
      String forwarding, String trustedProxies, java.util.List<String> invalid) {
    if ("framework".equalsIgnoreCase(forwarding)
        && (trustedProxies.isBlank() || !invalid.isEmpty()))
      throw McpOAuth.error(503, "MCP_TRUSTED_PROXIES_REQUIRED");
  }
}
