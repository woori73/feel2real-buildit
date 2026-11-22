package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 제작 현황판(/dashboard/factory) API. /api/admin/** = ADMIN(SecurityConfig). 본인 작업만 다룬다. */
@RestController
@RequestMapping("/api/admin/coord/factory")
public class CoordFactoryController {

  private final CoordFactoryService service;

  public CoordFactoryController(CoordFactoryService service) {
    this.service = service;
  }

  /** 이미 가져감·이미 게시됨·검수 부족처럼 지금 상태에서 할 수 없는 일 = 409 + 안내 문장. */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> conflict(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
  }

  @GetMapping
  public Map<String, Object> board(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.board(p);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody JsonNode body) {
    return service.create(p, body);
  }

  @GetMapping("/{jobId}/brief")
  public Map<String, Object> brief(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String jobId) {
    return service.brief(p, jobId);
  }

  @PostMapping("/{jobId}/cancel")
  public Map<String, Object> cancel(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String jobId) {
    return service.cancel(p, jobId);
  }

  @PostMapping("/{jobId}/publish/approve")
  public Map<String, Object> approve(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String jobId) {
    return service.approvePublish(p, jobId);
  }

  public record Published(String url) {}

  @PostMapping("/{jobId}/publish/done")
  public Map<String, Object> done(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String jobId,
      @RequestBody Published body) {
    return service.markPublished(p, jobId, body == null ? null : body.url());
  }

  public record Withdraw(String reason) {}

  @PostMapping("/{jobId}/publish/withdraw")
  public Map<String, Object> withdraw(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String jobId,
      @RequestBody(required = false) Withdraw body) {
    return service.withdrawPublish(p, jobId, body == null ? null : body.reason());
  }
}
