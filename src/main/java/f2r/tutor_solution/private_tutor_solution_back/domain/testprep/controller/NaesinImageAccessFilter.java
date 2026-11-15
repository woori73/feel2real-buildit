package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.NaesinAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 내신 문항 그림(/naesin/{세트}/rNN/...) 이용 범위. 로그인 여부는 SecurityConfig가 먼저 막고,
 * 여기서는 2회부터(r02~)의 그림을 관리자·재원생에게만 보낸다. 1회(r01) 그림은 로그인 회원 누구나.
 */
@Component
@RequiredArgsConstructor
public class NaesinImageAccessFilter extends OncePerRequestFilter {

  private static final Pattern ROUND_IMAGE = Pattern.compile("^/naesin/[^/]+/r(\\d+)/[^/]+$");

  private final NaesinAccessService naesinAccessService;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/naesin/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Matcher m = ROUND_IMAGE.matcher(request.getRequestURI());
    if (m.matches() && Integer.parseInt(m.group(1)) != 1 && !naesinAccessService.hasFullAccess(principal())) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    chain.doFilter(request, response);
  }

  private static AuthenticatedPrincipal principal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal p ? p : null;
  }
}
