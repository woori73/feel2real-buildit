package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCardEventApproveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCardEventRejectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCardEventReviewResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessCardAccountingService;
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
 * Easy Phase 5A.1 — admin review of provider-neutral card events, and posting to the
 * existing EASY ledger. {@code /api/admin/**} → ROLE_ADMIN.
 * No real card-issuer collection here; events already exist via the Phase 5A observation API.
 */
@RestController
@RequestMapping("/api/admin/easy/card-events")
@RequiredArgsConstructor
public class EasyCardReviewController {

  private final EasyBusinessCardAccountingService accountingService;

  @GetMapping
  public ResponseEntity<List<EasyCardEventReviewResponse>> pendingReview(
      @RequestParam Long businessId) {
    return ResponseEntity.ok(accountingService.listPendingReview(businessId).stream()
        .map(EasyCardEventReviewResponse::from)
        .toList());
  }

  @PostMapping("/{eventId}/approve")
  public ResponseEntity<EasyCardEventReviewResponse> approve(
      @PathVariable Long eventId, @RequestBody EasyCardEventApproveRequest request) {
    var event = accountingService.approveAndPost(
        eventId, request.businessId(), request.accountId(), "admin");
    return ResponseEntity.ok(EasyCardEventReviewResponse.from(event));
  }

  @PostMapping("/{eventId}/reject")
  public ResponseEntity<EasyCardEventReviewResponse> reject(
      @PathVariable Long eventId, @RequestBody EasyCardEventRejectRequest request) {
    var event = accountingService.rejectEvent(eventId, request.businessId(), "admin");
    return ResponseEntity.ok(EasyCardEventReviewResponse.from(event));
  }
}
