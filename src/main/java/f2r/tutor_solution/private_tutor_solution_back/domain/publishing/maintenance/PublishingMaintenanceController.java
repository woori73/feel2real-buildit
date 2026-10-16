package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

/** Existing /api/admin/** security plus defense in depth; no unauthenticated control plane. */
@RestController
@RequestMapping("/api/admin/publishing/maintenance")
public class PublishingMaintenanceController {
  private final PublishingMaintenanceGate gate;
  public PublishingMaintenanceController(PublishingMaintenanceGate gate) { this.gate = gate; }
  private void admin() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream()
        .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }
  private void control(HttpServletRequest request) {
    admin();
    // Cross-site forms cannot set this header; CORS is still enforced by existing security.
    if (!"1".equals(request.getHeader("X-DesignCraft-Maintenance"))
        || !WebUtils.isSameOrigin(new org.springframework.http.server.ServletServerHttpRequest(request))
        || (request.getHeader("Sec-Fetch-Site") != null
            && !"same-origin".equals(request.getHeader("Sec-Fetch-Site"))))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }
  @GetMapping public PublishingMaintenanceGate.Status status() { admin(); return gate.status(); }
  @PostMapping("/drain") public PublishingMaintenanceGate.Status drain(HttpServletRequest request) {
    control(request); return gate.drain();
  }
  @PostMapping("/resume") public PublishingMaintenanceGate.Status resume(HttpServletRequest request) {
    control(request); return gate.resume();
  }
}
