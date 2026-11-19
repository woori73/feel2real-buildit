package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 승인 화면(/admin/coord/approvals) API. /api/admin/** = ADMIN(SecurityConfig). 본인 요청만 다룬다. */
@RestController
@RequestMapping("/api/admin/coord/jobs")
public class CoordinatorController {

  private final CoordinatorService service;

  public CoordinatorController(CoordinatorService service) {
    this.service = service;
  }

  /** 이미 처리됨·시간 초과·화면이 최신이 아님 = 409 + 안내 문장. */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
    return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
  }

  @GetMapping
  public List<Map<String, Object>> list(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.list(p, "");
  }

  @GetMapping("/{jobId}")
  public Map<String, Object> get(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String jobId) {
    return service.get(p, jobId, "", true);
  }

  public record Decision(String planHash) {}

  @PostMapping("/{jobId}/approve")
  public Map<String, Object> approve(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String jobId,
      @RequestBody Decision d) {
    return service.approve(p, jobId, d == null ? null : d.planHash(), "");
  }

  @PostMapping("/{jobId}/reject")
  public Map<String, Object> reject(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String jobId) {
    return service.reject(p, jobId, "");
  }
}
