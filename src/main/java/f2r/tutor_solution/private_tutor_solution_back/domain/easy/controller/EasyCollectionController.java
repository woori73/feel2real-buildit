package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionConnectionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionCheckpointResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionManagementResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionSourceResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionIngestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionManagementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionSchedulerService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 자동수집 foundation 관리자 API — {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 * CSV 업로드 → 수집 run → 기존 import staging 인계까지. confirm은 기존
 * {@code /api/admin/easy/imports/{batchId}/confirm}만 유효하며 이 컨트롤러는 확정을 제공하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/easy/collections")
@RequiredArgsConstructor
public class EasyCollectionController {

  private final EasyCollectionIngestService ingestService;
  private final EasyCollectionSchedulerService schedulerService;
  private final EasyCollectionManagementService managementService;

  @PostMapping("/ingest/csv")
  public ResponseEntity<EasyCollectionIngestResponse> ingestCsv(
      @RequestParam("businessId") Long businessId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "label", required = false) String label) {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("파일이 비어 있습니다.");
    }
    try {
      return ResponseEntity.ok(ingestService.ingestCsv(
          businessId, label, file.getOriginalFilename(), file.getBytes()));
    } catch (IOException e) {
      throw new UncheckedIOException("파일 읽기에 실패했습니다.", e);
    }
  }

  @GetMapping("/runs")
  public ResponseEntity<List<EasyCollectionRunResponse>> listRuns(
      @RequestParam("businessId") Long businessId) {
    return ResponseEntity.ok(ingestService.listRuns(businessId));
  }

  @GetMapping("/runs/{runId}")
  public ResponseEntity<EasyCollectionRunResponse> getRun(@PathVariable Long runId) {
    return ResponseEntity.ok(ingestService.getRun(runId));
  }

  @GetMapping("/sources")
  public ResponseEntity<List<EasyCollectionSourceResponse>> listSources() {
    return ResponseEntity.ok(ingestService.listSources());
  }


  @GetMapping("/connections")
  public ResponseEntity<List<EasyCollectionManagementResponse>> listConnections(
      @RequestParam("businessId") Long businessId) {
    return ResponseEntity.ok(managementService.listConnections(businessId));
  }

  @PutMapping("/connections/{connectionId}/enabled")
  public ResponseEntity<EasyCollectionManagementResponse> setConnectionEnabled(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId,
      @RequestBody ConnectionEnabledRequest request) {
    return ResponseEntity.ok(request.enabled()
        ? managementService.enable(businessId, connectionId)
        : managementService.disable(businessId, connectionId));
  }

  @GetMapping("/connections/{connectionId}/checkpoints")
  public ResponseEntity<List<EasyCollectionCheckpointResponse>> listCheckpoints(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId) {
    return ResponseEntity.ok(managementService.listCheckpoints(businessId, connectionId));
  }

  @PostMapping("/connections/{connectionId}/run")
  public ResponseEntity<EasyCollectionRunResponse> triggerManualCollection(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId) {
    return ResponseEntity.ok(managementService.triggerManual(businessId, connectionId));
  }

  /**
   * 연결 생성 또는 갱신 (connection_ref 포함) — 관리자 콘솔에서 초기 등록용.
   */
  @PostMapping("/connections")
  public ResponseEntity<EasyCollectionConnectionResponse> createConnection(
      @RequestParam("businessId") Long businessId,
      @RequestParam("sourceCode") String sourceCode,
      @RequestParam(value = "label", required = false) String label,
      @RequestParam(value = "connectionRef", required = false) String connectionRef) {
    EasyCollectionConnection conn = ingestService.createOrUpdateConnection(businessId, sourceCode, label, connectionRef);
    return ResponseEntity.ok(EasyCollectionConnectionResponse.from(conn));
  }

  /**
   * connection_ref 업데이트 — 관리자 콘솔에서 가맹점 동의ID 등 참조자 등록/수정.
   * NEEDS_REAUTH 상태면 ACTIVE로 복구.
   */
  @PutMapping("/connections/{connectionId}/ref")
  public ResponseEntity<EasyCollectionConnectionResponse> updateConnectionRef(
      @PathVariable Long connectionId,
      @RequestBody ConnectionRefRequest request,
      @RequestParam("businessId") Long businessId) {
    ingestService.updateConnectionRef(businessId, connectionId, request.connectionRef());
    EasyCollectionConnection conn = ingestService.getConnection(connectionId);
    return ResponseEntity.ok(EasyCollectionConnectionResponse.from(conn));
  }

  public record ConnectionRefRequest(String connectionRef) {}

  public record ConnectionEnabledRequest(boolean enabled) {}
}
