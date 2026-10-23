package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller.AdmissionsRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** ALB peer validation must use the socket peer, before framework forwarded-header wrappers. */
final class McpClientAddress {
  private McpClientAddress() {}

  static String resolve(HttpServletRequest request, AdmissionsRateLimiter addresses) {
    HttpServletRequest raw = request;
    while (raw instanceof HttpServletRequestWrapper wrapper
        && wrapper.getRequest() instanceof HttpServletRequest original) raw = original;
    return addresses.resolveClientIp(raw);
  }
}
