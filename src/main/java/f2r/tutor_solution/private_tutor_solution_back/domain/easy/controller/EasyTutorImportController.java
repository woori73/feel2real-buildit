package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorImportService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tutor/easy/imports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
public class EasyTutorImportController {

  private final EasyTutorImportService importService;

  @GetMapping
  public ResponseEntity<List<EasyImportBatchResponse>> list(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId) {
    return ResponseEntity.ok(importService.list(principal, businessId));
  }

  @GetMapping("/{batchId}")
  public ResponseEntity<EasyImportBatchResponse> detail(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId, @PathVariable Long batchId) {
    return ResponseEntity.ok(importService.detail(principal, businessId, batchId));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<EasyImportBatchResponse> upload(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId, @RequestPart("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("CSV 파일이 비어 있습니다.");
    }
    return ResponseEntity.ok(importService.uploadCsv(
        principal, businessId, file.getOriginalFilename(), file.getInputStream()));
  }

  @PutMapping("/{batchId}/rows/{rowId}/account")
  public ResponseEntity<EasyImportBatchResponse> selectAccount(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId, @PathVariable Long batchId, @PathVariable Long rowId,
      @RequestBody AccountSelection request) {
    return ResponseEntity.ok(importService.selectAccount(
        principal, businessId, batchId, rowId, request.accountId()));
  }

  @PostMapping("/{batchId}/reclassify")
  public ResponseEntity<EasyImportBatchResponse> reclassify(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId, @PathVariable Long batchId,
      @RequestBody Map<String, List<Long>> body) {
    return ResponseEntity.ok(importService.reclassify(
        principal, businessId, batchId, body == null ? null : body.get("rowIds")));
  }

  @PostMapping("/{batchId}/confirm")
  public ResponseEntity<EasyImportBatchResponse> confirm(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId, @PathVariable Long batchId,
      @RequestBody(required = false) Map<String, List<Long>> body) {
    return ResponseEntity.ok(importService.confirm(
        principal, businessId, batchId, body == null ? null : body.get("rowIds")));
  }

  @DeleteMapping("/{batchId}")
  public ResponseEntity<Void> discard(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId, @PathVariable Long batchId) {
    importService.discard(principal, businessId, batchId);
    return ResponseEntity.noContent().build();
  }

  public record AccountSelection(Long accountId) {
  }
}
