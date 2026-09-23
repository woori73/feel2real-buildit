package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.EasyCollectorPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectorTaskResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTaskStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectorCredentialGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectorTaskService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLocalCollectorService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visible Local Browser Collector — agent callback surface. Authenticated by
 * {@code EasyCollectorAgentTokenFilter} (per-collector Bearer token), never ROLE_ADMIN alone.
 * The agent may only ever report status transitions and, on completion, file metadata
 * (filename/size/SHA-256) — never file content, never any credential. {@code completed}/
 * {@code failed} take a raw map so {@link EasyCollectorCredentialGuard} can reject a payload
 * shaped like a credential instead of silently dropping it.
 */
@RestController
@RequestMapping("/api/admin/easy/collector-agent")
@RequiredArgsConstructor
public class EasyCollectorAgentController {

  private final EasyLocalCollectorService collectorService;
  private final EasyCollectorTaskService taskService;

  @PostMapping("/heartbeat")
  public ResponseEntity<Void> heartbeat(Authentication authentication) {
    collectorService.heartbeat(collectorId(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/next-task")
  public ResponseEntity<EasyCollectorTaskResponse> nextTask(Authentication authentication) {
    return taskService.claimNext(collectorId(authentication))
        .map(task -> ResponseEntity.ok(EasyCollectorTaskResponse.from(task)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/tasks/{taskId}/waiting-for-auth")
  public ResponseEntity<EasyCollectorTaskResponse> waitingForAuth(
      @PathVariable Long taskId, Authentication authentication) {
    return advance(taskId, authentication, EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH);
  }

  @PostMapping("/tasks/{taskId}/resume")
  public ResponseEntity<EasyCollectorTaskResponse> resume(
      @PathVariable Long taskId, Authentication authentication) {
    return advance(taskId, authentication, EasyCollectorTaskStatus.AUTHENTICATED);
  }

  @PostMapping("/tasks/{taskId}/collecting")
  public ResponseEntity<EasyCollectorTaskResponse> collecting(
      @PathVariable Long taskId, Authentication authentication) {
    return advance(taskId, authentication, EasyCollectorTaskStatus.COLLECTING);
  }

  @PostMapping("/tasks/{taskId}/downloading")
  public ResponseEntity<EasyCollectorTaskResponse> downloading(
      @PathVariable Long taskId, Authentication authentication) {
    return advance(taskId, authentication, EasyCollectorTaskStatus.DOWNLOADING);
  }

  @PostMapping("/tasks/{taskId}/completed")
  public ResponseEntity<EasyCollectorTaskResponse> completed(
      @PathVariable Long taskId, @RequestBody Map<String, Object> body,
      Authentication authentication) {
    EasyCollectorCredentialGuard.reject(body);
    String filename = requireString(body, "filename");
    long size = requireLong(body, "size");
    String sha256 = requireString(body, "sha256");
    var task = taskService.complete(taskId, collectorId(authentication), filename, size, sha256);
    return ResponseEntity.ok(EasyCollectorTaskResponse.from(task));
  }

  @PostMapping("/tasks/{taskId}/failed")
  public ResponseEntity<EasyCollectorTaskResponse> failed(
      @PathVariable Long taskId, @RequestBody Map<String, Object> body,
      Authentication authentication) {
    EasyCollectorCredentialGuard.reject(body);
    String reason = body == null ? null : String.valueOf(body.get("reason"));
    var task = taskService.fail(taskId, collectorId(authentication), reason);
    return ResponseEntity.ok(EasyCollectorTaskResponse.from(task));
  }

  private ResponseEntity<EasyCollectorTaskResponse> advance(
      Long taskId, Authentication authentication, EasyCollectorTaskStatus next) {
    var task = taskService.advance(taskId, collectorId(authentication), next);
    return ResponseEntity.ok(EasyCollectorTaskResponse.from(task));
  }

  private Long collectorId(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof EasyCollectorPrincipal principal)) {
      throw new IllegalStateException("Not authenticated as a collector agent.");
    }
    return principal.collectorId();
  }

  private String requireString(Map<String, Object> body, String key) {
    Object value = body == null ? null : body.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new IllegalArgumentException(key + " is required.");
    }
    return String.valueOf(value);
  }

  private long requireLong(Map<String, Object> body, String key) {
    Object value = body == null ? null : body.get(key);
    if (value == null) throw new IllegalArgumentException(key + " is required.");
    if (value instanceof Number number) return number.longValue();
    return Long.parseLong(String.valueOf(value));
  }
}
