package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal.AgentTokenService;
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
 * 시각 편집기 AI Agent 전용 Bearer 토큰 인증.
 *
 * <p>{@link EasyCollectorAgentTokenFilter}와 같은 패턴: <b>좁은 allowlist</b>(컨텍스트 조회·제안 생성·제안 상태 조회)의
 * 경로에서만 토큰을 검사하고, 토큰은 학원 1개에 바인딩된다. 초안/게시본/게시/renderMode 등 어떤 쓰기 경로도
 * allowlist에 없다. 토큰이 없거나 무효이면 인증을 설정하지 않으므로 {@code hasRole("VE_AGENT")} 규칙에서 거부된다
 * (fail-closed). 기존 인증(사용자 쿠키)은 덮어쓰지 않고, 원문 토큰은 로그에 남기지 않는다.
 */
public class VisualEditorAgentTokenFilter extends OncePerRequestFilter {

  public static final String ROLE = "VE_AGENT";

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private static final List<AgentRoute> ALLOWLIST = List.of(
      new AgentRoute("GET", "/api/agent/academy/visual-editor/context"),
      new AgentRoute("POST", "/api/agent/academy/visual-editor/proposals"),
      new AgentRoute("GET", "/api/agent/academy/visual-editor/proposals/*"));

  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private final ObjectProvider<AgentTokenService> tokenServiceProvider;

  public VisualEditorAgentTokenFilter(ObjectProvider<AgentTokenService> tokenServiceProvider) {
    this.tokenServiceProvider = tokenServiceProvider;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    boolean authenticatedHere = false;
    try {
      if (SecurityContextHolder.getContext().getAuthentication() == null
          && isAllowlisted(request.getMethod(), request.getRequestURI())) {
        AgentTokenService service = tokenServiceProvider.getIfAvailable();
        if (service != null) {
          Optional<AgentTokenService.AgentPrincipal> principal =
              service.authenticate(bearerToken(request));
          if (principal.isPresent()) {
            var auth = new UsernamePasswordAuthenticationToken(
                principal.get(), null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
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
