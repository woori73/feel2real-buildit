package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLocalCollectorService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Visible Local Browser Collector agent — Bearer token auth, one token per registered collector.
 *
 * <p>Modeled on {@link ChartAgentTokenFilter}: a narrow allowlist of collector-callback routes
 * only, never any admin/management endpoint. Unlike ChartAgentTokenFilter's single shared env
 * secret, each collector has its own token (per-collector row in {@code easy_local_collectors},
 * only its SHA-256 stored) — so this filter does a per-request lookup via
 * {@link EasyLocalCollectorService#authenticate}, not a constant-time string compare against one
 * fixed value.
 *
 * <p>Safety:
 * <ul>
 *   <li>Unknown/revoked/missing token -&gt; no authentication set, request falls through to the
 *       normal ROLE_ADMIN rule and is rejected (fail-closed).
 *   <li>Allowlist-only — no admin/management route is ever reachable via this filter.
 *   <li>Existing authentication (admin JWT cookie) is never overwritten.
 *   <li>The raw token is never logged; only its SHA-256 is compared/stored (in the service).
 * </ul>
 */
public class EasyCollectorAgentTokenFilter extends OncePerRequestFilter {

  public static final String ROLE = "EASY_COLLECTOR_AGENT";

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private static final List<AgentRoute> ALLOWLIST = List.of(
      new AgentRoute("POST", "/api/admin/easy/collector-agent/heartbeat"),
      new AgentRoute("GET", "/api/admin/easy/collector-agent/next-task"),
      new AgentRoute("POST", "/api/admin/easy/collector-agent/tasks/*/waiting-for-auth"),
      new AgentRoute("POST", "/api/admin/easy/collector-agent/tasks/*/resume"),
      new AgentRoute("POST", "/api/admin/easy/collector-agent/tasks/*/collecting"),
      new AgentRoute("POST", "/api/admin/easy/collector-agent/tasks/*/downloading"),
      new AgentRoute("POST", "/api/admin/easy/collector-agent/tasks/*/completed"),
      new AgentRoute("POST", "/api/admin/easy/collector-agent/tasks/*/failed"));

  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private final ObjectProvider<EasyLocalCollectorService> collectorServiceProvider;

  /** Slice-compatible: {@code @WebMvcTest} without the JPA-backed service simply never authenticates. */
  public EasyCollectorAgentTokenFilter(
      ObjectProvider<EasyLocalCollectorService> collectorServiceProvider) {
    this.collectorServiceProvider = collectorServiceProvider;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    boolean authenticatedHere = false;
    try {
      if (SecurityContextHolder.getContext().getAuthentication() == null
          && isAllowlisted(request.getMethod(), request.getRequestURI())) {
        EasyLocalCollectorService service = collectorServiceProvider.getIfAvailable();
        String presented = bearerToken(request);
        if (service != null) {
          Optional<EasyLocalCollector> collector = service.authenticate(presented);
          if (collector.isPresent()) {
            EasyCollectorPrincipal principal = new EasyCollectorPrincipal(
                collector.get().getCollectorId(), collector.get().getBusiness().getBusinessId());
            var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
            SecurityContextHolder.getContext().setAuthentication(auth);
            authenticatedHere = true;
          }
        }
      }
      chain.doFilter(request, response);
    } finally {
      if (authenticatedHere) {
        SecurityContextHolder.clearContext();
      }
    }
  }

  private boolean isAllowlisted(String method, String uri) {
    for (AgentRoute route : ALLOWLIST) {
      if (route.method().equalsIgnoreCase(method) && pathMatcher.match(route.pattern(), uri)) {
        return true;
      }
    }
    return false;
  }

  private String bearerToken(HttpServletRequest request) {
    String header = request.getHeader(AUTH_HEADER);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length()).trim();
    }
    return null;
  }

  private record AgentRoute(String method, String pattern) {}
}
