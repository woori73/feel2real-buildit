package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyClosingResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewResolveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWithholdingRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWithholdingResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAuditService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Easy AI 감사 기반 — "AI가 확인할 장부" 큐 + 원천징수 검토 + 연도 마감.
 * {@code /api/admin/**} → ROLE_ADMIN. 세법 판정·신고 기능은 없다.
 */
@RestController
@RequestMapping("/api/admin/easy")
@RequiredArgsConstructor
public class EasyAuditController {

  private final EasyAuditService auditService;

  @GetMapping("/reviews/summary")
  public ResponseEntity<EasyReviewSummaryResponse> reviewSummary(
      @RequestParam(value = "businessId", required = false) Long businessId) {
    return ResponseEntity.ok(auditService.reviewSummary(businessId));
  }

  @GetMapping("/reviews")
  public ResponseEntity<List<EasyReviewItemResponse>> listReviews(
      @RequestParam(value = "businessId", required = false) Long businessId,
      @RequestParam(value = "kind", required = false) String kind,
      @RequestParam(value = "status", required = false) String status) {
    return ResponseEntity.ok(auditService.listReviews(businessId, kind, status));
  }

  @PostMapping("/reviews/rebuild")
  public ResponseEntity<EasyReviewSummaryResponse> rebuild(
      @RequestParam(value = "businessId", required = false) Long businessId) {
    return ResponseEntity.ok(auditService.rebuild(businessId));
  }

  @PostMapping("/reviews/{reviewId}/resolve")
  public ResponseEntity<EasyReviewItemResponse> resolve(
      @PathVariable Long reviewId,
      @RequestBody(required = false) EasyReviewResolveRequest request) {
    return ResponseEntity.ok(auditService.resolve(reviewId, request, "admin"));
  }

  @PostMapping("/reviews/flag-business")
  public ResponseEntity<EasyReviewItemResponse> flagBusiness(
      @RequestBody Map<String, Object> body) {
    Object rawTxn = body == null ? null : body.get("txnId");
    if (rawTxn == null) {
      throw new IllegalArgumentException("거래를 선택해 주세요.");
    }
    Long txnId = Long.valueOf(String.valueOf(rawTxn));
    Object rawNote = body == null ? null : body.get("note");
    String note = rawNote == null ? null : String.valueOf(rawNote);
    return ResponseEntity.ok(auditService.flagBusinessCheck(txnId, note));
  }

  @GetMapping("/withholdings")
  public ResponseEntity<List<EasyWithholdingResponse>> listWithholdings(
      @RequestParam(value = "businessId", required = false) Long businessId) {
    return ResponseEntity.ok(auditService.listWithholdings(businessId));
  }

  @PostMapping("/withholdings")
  public ResponseEntity<EasyWithholdingResponse> createWithholding(
      @RequestBody EasyWithholdingRequest request) {
    return ResponseEntity.ok(auditService.createWithholding(request));
  }

  @PutMapping("/withholdings/{recordId}")
  public ResponseEntity<EasyWithholdingResponse> updateWithholding(
      @PathVariable Long recordId, @RequestBody EasyWithholdingRequest request) {
    return ResponseEntity.ok(auditService.updateWithholding(recordId, request));
  }

  @DeleteMapping("/withholdings/{recordId}")
  public ResponseEntity<Void> deleteWithholding(@PathVariable Long recordId) {
    auditService.deleteWithholding(recordId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/closings")
  public ResponseEntity<List<EasyClosingResponse>> listClosings(
      @RequestParam(value = "businessId", required = false) Long businessId,
      @RequestParam(value = "fiscalYear", required = false) Integer fiscalYear) {
    return ResponseEntity.ok(auditService.listClosings(businessId, fiscalYear));
  }

  @PostMapping("/closings")
  public ResponseEntity<EasyClosingResponse> openClosing(
      @RequestBody Map<String, Object> body) {
    Object rawBusiness = body == null ? null : body.get("businessId");
    Object rawYear = body == null ? null : body.get("fiscalYear");
    if (rawBusiness == null || rawYear == null) {
      throw new IllegalArgumentException("사업체와 귀속연도를 입력해 주세요.");
    }
    return ResponseEntity.ok(auditService.openClosing(
        Long.valueOf(String.valueOf(rawBusiness)),
        Integer.parseInt(String.valueOf(rawYear))));
  }

  @PostMapping("/closings/{closingId}/review")
  public ResponseEntity<EasyClosingResponse> markReview(@PathVariable Long closingId) {
    return ResponseEntity.ok(auditService.markClosingReview(closingId));
  }

  @PostMapping("/closings/{closingId}/close")
  public ResponseEntity<EasyClosingResponse> close(
      @PathVariable Long closingId,
      @RequestBody(required = false) Map<String, Object> body) {
    Object rawNote = body == null ? null : body.get("note");
    Object rawForce = body == null ? null : body.get("force");
    String note = rawNote == null ? null : String.valueOf(rawNote);
    boolean force = Boolean.parseBoolean(String.valueOf(rawForce));
    return ResponseEntity.ok(auditService.closeClosing(closingId, note, force));
  }

  @PostMapping("/closings/{closingId}/reopen")
  public ResponseEntity<EasyClosingResponse> reopen(@PathVariable Long closingId) {
    return ResponseEntity.ok(auditService.reopenClosing(closingId));
  }
}
