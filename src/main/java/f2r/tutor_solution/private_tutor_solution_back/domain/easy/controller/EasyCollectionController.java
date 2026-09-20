package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionSourceResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionIngestService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
