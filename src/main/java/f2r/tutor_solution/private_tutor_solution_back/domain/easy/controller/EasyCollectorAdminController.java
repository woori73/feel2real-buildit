package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectorRegisterRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectorRegisterResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectorTaskCancelRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectorTaskResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectorTaskStartRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyLocalCollectorResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectorTaskService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLocalCollectorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visible Local Browser Collector — admin management. {@code /api/admin/**} → ROLE_ADMIN.
 * Pairing (register) and task queueing/cancel/status only; the collector agent itself calls a
 * separate, narrow-scoped endpoint set ({@link EasyCollectorAgentController}).
 */
@RestController
@RequestMapping("/api/admin/easy/collector")
@RequiredArgsConstructor
public class EasyCollectorAdminController {

  private final EasyLocalCollectorService collectorService;
  private final EasyCollectorTaskService taskService;

  @PostMapping("/register")
  public ResponseEntity<EasyCollectorRegisterResponse> register(
      @RequestBody EasyCollectorRegisterRequest request) {
    var result = collectorService.register(request.businessId(), request.name());
    return ResponseEntity.ok(new EasyCollectorRegisterResponse(
        result.collector().getCollectorId(), result.collector().getName(), result.rawToken()));
  }

  @GetMapping
  public ResponseEntity<List<EasyLocalCollectorResponse>> list(
      @RequestParam Long businessId) {
    return ResponseEntity.ok(collectorService.list(businessId).stream()
        .map(EasyLocalCollectorResponse::from)
        .toList());
  }

  @PostMapping("/tasks")
  public ResponseEntity<EasyCollectorTaskResponse> startTask(
      @RequestBody EasyCollectorTaskStartRequest request) {
    var task = taskService.start(request.businessId(), request.collectorId(),
        request.sourceCode(), request.dataType(), request.requestedFrom(),
        request.requestedTo());
    return ResponseEntity.ok(EasyCollectorTaskResponse.from(task));
  }

  @GetMapping("/tasks")
  public ResponseEntity<List<EasyCollectorTaskResponse>> listTasks(
      @RequestParam Long businessId) {
    return ResponseEntity.ok(taskService.list(businessId).stream()
        .map(EasyCollectorTaskResponse::from)
        .toList());
  }

  @GetMapping("/tasks/{taskId}")
  public ResponseEntity<EasyCollectorTaskResponse> taskStatus(
      @PathVariable Long taskId, @RequestParam Long businessId) {
    return ResponseEntity.ok(EasyCollectorTaskResponse.from(taskService.status(taskId, businessId)));
  }

  @PostMapping("/tasks/{taskId}/cancel")
  public ResponseEntity<EasyCollectorTaskResponse> cancelTask(
      @PathVariable Long taskId, @RequestBody EasyCollectorTaskCancelRequest request) {
    var task = taskService.cancel(taskId, request.businessId());
    return ResponseEntity.ok(EasyCollectorTaskResponse.from(task));
  }
}
