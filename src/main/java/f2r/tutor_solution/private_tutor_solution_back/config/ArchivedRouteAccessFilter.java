package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies archive authentication and ADMIN policy after JWT authentication. */
public class ArchivedRouteAccessFilter extends OncePerRequestFilter {

  private final ArchivedRoutesProperties properties;
  private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

  public ArchivedRouteAccessFilter(ArchivedRoutesProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !Boolean.TRUE.equals(request.getAttribute(ArchivedRouteAliasFilter.ARCHIVED_ATTRIBUTE));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || trustResolver.isAnonymous(authentication)) {
      response.sendError(HttpStatus.UNAUTHORIZED.value());
      return;
    }
    if (properties.requireAdmin() && authentication.getAuthorities().stream()
        .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
      response.sendError(HttpStatus.FORBIDDEN.value());
      return;
    }
    filterChain.doFilter(request, response);
  }
}
