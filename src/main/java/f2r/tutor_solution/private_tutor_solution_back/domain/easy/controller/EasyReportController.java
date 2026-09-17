package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFilingCheckResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFilingReferenceRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFilingReferenceResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyIncomeTaxResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyYearPlResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyReportService;
import java.util.List;
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
 * Easy 결과 집계 — 연도 손익·신고자료 대조·종소세 준비.
 * {@code /api/admin/**} → ROLE_ADMIN. 계산식 역산·세액 확정은 하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/easy")
@RequiredArgsConstructor
public class EasyReportController {

  private final EasyReportService reportService;

  @GetMapping("/pl")
  public ResponseEntity<EasyYearPlResponse> yearPl(
      @RequestParam(value = "businessId", required = false) Long businessId,
      @RequestParam(value = "year", required = false) Integer year) {
    int fiscalYear = year == null ? java.time.LocalDate.now().getYear() : year;
    return ResponseEntity.ok(reportService.yearPl(businessId, fiscalYear));
  }

  @GetMapping("/filing-references")
  public ResponseEntity<List<EasyFilingReferenceResponse>> listReferences(
      @RequestParam(value = "businessId", required = false) Long businessId,
      @RequestParam(value = "fiscalYear", required = false) Integer fiscalYear) {
    return ResponseEntity.ok(reportService.listReferences(businessId, fiscalYear));
  }

  @PostMapping("/filing-references")
  public ResponseEntity<EasyFilingReferenceResponse> upsertReference(
      @RequestBody EasyFilingReferenceRequest request) {
    return ResponseEntity.ok(reportService.upsertReference(request));
  }

  @DeleteMapping("/filing-references/{refId}")
  public ResponseEntity<Void> deleteReference(@PathVariable Long refId) {
    reportService.deleteReference(refId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/filing-check")
  public ResponseEntity<EasyFilingCheckResponse> filingCheck(
      @RequestParam(value = "businessId") Long businessId,
      @RequestParam(value = "fiscalYear") int fiscalYear) {
    return ResponseEntity.ok(reportService.filingCheck(businessId, fiscalYear));
  }

  @GetMapping("/income-tax")
  public ResponseEntity<EasyIncomeTaxResponse> incomeTax(
      @RequestParam(value = "year", required = false) Integer year) {
    int fiscalYear = year == null ? java.time.LocalDate.now().getYear() : year;
    return ResponseEntity.ok(reportService.incomeTax(fiscalYear));
  }
}
