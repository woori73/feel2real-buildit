package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceTransactionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewResolveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyWorkspaceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAuditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessScopeGuard;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 Easy Workspace 2A API. businessId는 모든 요청에서 필수다. */
@RestController
@RequestMapping("/api/admin/easy/workspace")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EasyWorkspaceController {

  private final EasyWorkspaceService workspaceService;
  private final EasyAuditService auditService;
  private final EasyBusinessScopeGuard scopeGuard;

  @GetMapping("/monthly")
  public ResponseEntity<EasyWorkspaceMonthlyResponse> monthly(
      @RequestParam Long businessId,
      @RequestParam(required = false)
      @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return ResponseEntity.ok(workspaceService.monthly(businessId, month));
  }

  @GetMapping("/transactions")
  public ResponseEntity<List<EasyWorkspaceTransactionResponse>> transactions(
      @RequestParam Long businessId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Long accountId) {
    return ResponseEntity.ok(workspaceService.transactions(
        businessId, from, to, parseDirection(direction), category, accountId));
  }

  @GetMapping("/transactions/{txnId}")
  public ResponseEntity<EasyWorkspaceTransactionResponse> transaction(
      @RequestParam Long businessId, @PathVariable Long txnId) {
    return ResponseEntity.ok(workspaceService.transaction(businessId, txnId));
  }

  @PostMapping("/transactions")
  public ResponseEntity<EasyWorkspaceTransactionResponse> createTransaction(
      @RequestBody EasyTxnRequest request) {
    return ResponseEntity.ok(workspaceService.createTransaction(request));
  }

  @PutMapping("/transactions/{txnId}")
  public ResponseEntity<EasyWorkspaceTransactionResponse> updateTransaction(
      @RequestParam Long businessId,
      @PathVariable Long txnId,
      @RequestBody EasyTxnRequest request) {
    return ResponseEntity.ok(
        workspaceService.updateTransaction(businessId, txnId, request));
  }

  @GetMapping("/reviews")
  public ResponseEntity<List<EasyReviewItemResponse>> reviews(
      @RequestParam Long businessId,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String status) {
    scopeGuard.requireActive(businessId);
    return ResponseEntity.ok(auditService.listReviews(businessId, kind, status));
  }

  @PostMapping("/reviews/{reviewId}/approve")
  public ResponseEntity<EasyReviewItemResponse> approveReview(
      @RequestParam Long businessId,
      @PathVariable Long reviewId,
      @RequestBody(required = false) EasyReviewResolveRequest request) {
    return ResponseEntity.ok(
        auditService.resolveWorkspace(businessId, reviewId, request, "admin"));
  }

  @PostMapping("/reviews/{reviewId}/hold")
  public ResponseEntity<EasyReviewItemResponse> holdReview(
      @RequestParam Long businessId, @PathVariable Long reviewId) {
    return ResponseEntity.ok(auditService.holdWorkspace(businessId, reviewId));
  }

  private EasyTxnDirection parseDirection(String direction) {
    if (direction == null || direction.isBlank()) {
      return null;
    }
    try {
      return EasyTxnDirection.valueOf(direction.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("수입/지출 값이 올바르지 않습니다.");
    }
  }
}
