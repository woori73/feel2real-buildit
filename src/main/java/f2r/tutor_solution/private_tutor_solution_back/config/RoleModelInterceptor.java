package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class RoleModelInterceptor implements HandlerInterceptor {

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView) {
    if (modelAndView == null) return;
    modelAndView.addObject("role", currentRole());
  }

  String currentRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof JwtTokenProvider.AuthenticatedPrincipal principal)) {
      return null;
    }
    return principal.role();
  }
}
