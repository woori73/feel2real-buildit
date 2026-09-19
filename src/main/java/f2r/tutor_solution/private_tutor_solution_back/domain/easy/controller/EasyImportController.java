package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyShopImportService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Easy import staging — {@code /api/admin/**} → ROLE_ADMIN.
 * 실제 이지샵 데이터 import는 Work 자료 도착 후 별도 작업에서 수행한다.
 */
@RestController
@RequestMapping("/api/admin/easy/imports")
@RequiredArgsConstructor
public class EasyImportController {

  private final EasyImportService importService;
  private final EasyShopImportService shopImportService;

  @GetMapping
  public ResponseEntity<List<EasyImportBatchResponse>> list(
      @RequestParam(value = "businessId", required = false) Long businessId) {
    return ResponseEntity.ok(importService.listBatches(businessId));
  }

  @GetMapping("/{batchId}")
  public ResponseEntity<EasyImportBatchResponse> detail(@PathVariable Long batchId) {
    return ResponseEntity.ok(importService.getBatch(batchId));
  }

  @PostMapping
  public ResponseEntity<EasyImportBatchResponse> stage(@RequestBody EasyImportBatchRequest request) {
    return ResponseEntity.ok(importService.stage(request));
  }

  /**
   * EasyShop 실데이터 staging — source_kind=EASYSHOP.
   * 사업체 코드가 섞인 파일은 사업장별 배치로 분리한다.
   */
  @PostMapping("/easyshop")
  public ResponseEntity<List<EasyImportBatchResponse>> stageEasyshop(
      @RequestBody EasyShopImportRequest request) {
    return ResponseEntity.ok(shopImportService.stage(request));
  }

  @PostMapping("/{batchId}/confirm")
  public ResponseEntity<EasyImportBatchResponse> confirm(
      @PathVariable Long batchId,
      @RequestBody(required = false) Map<String, List<Long>> body) {
    List<Long> rowIds = body == null ? null : body.get("rowIds");
    return ResponseEntity.ok(importService.confirm(batchId, rowIds));
  }

  @DeleteMapping("/{batchId}")
  public ResponseEntity<Void> discard(@PathVariable Long batchId) {
    importService.discard(batchId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{batchId}/reclassify")
  public ResponseEntity<EasyImportBatchResponse> reclassify(
      @PathVariable Long batchId,
      @RequestBody Map<String, List<Long>> body) {
    List<Long> rowIds = body == null ? null : body.get("rowIds");
    return ResponseEntity.ok(importService.reclassify(batchId, rowIds));
  }
}
