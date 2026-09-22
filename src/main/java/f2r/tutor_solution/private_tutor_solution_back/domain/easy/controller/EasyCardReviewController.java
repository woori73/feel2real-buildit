package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCardClassificationRecommendationResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCardEventApproveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCardEventRejectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCardEventReviewResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyMerchantMappingResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyMerchantMappingSaveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessCardAccountingService;
import java.util.List;
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
 * Easy Phase 5A.1/5A.2 — admin review of provider-neutral card events, classification recommendation,
 * merchant mapping management, and posting to the existing EASY ledger.
 * {@code /api/admin/**} → ROLE_ADMIN.
 * No real card-issuer collection here; events already exist via the Phase 5A observation API.
 * Auto-posting is NOT implemented — admin must explicitly approve via approveAndPost.
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
        .map(item -> EasyCardEventReviewResponse.from(item, item.recommendation()))
        .toList());
  }

  @GetMapping("/{eventId}/recommendation")
  public ResponseEntity<EasyCardClassificationRecommendationResponse> getRecommendation(
      @PathVariable Long eventId, @RequestParam Long businessId) {
    // This would require a method to get recommendation for a single event
    // For now, we rely on the listPendingReview which includes recommendation
    // A dedicated endpoint can be added if needed
    return ResponseEntity.ok().build();
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

  /**
   * Save explicit admin merchant → account mapping (Phase 5A.2).
   * Creates or updates mapping with source=ADMIN_MAPPING, confidence=HIGH.
   * This does NOT auto-post — it only saves the mapping for future recommendations.
   */
  @PostMapping("/merchant-mappings")
  public ResponseEntity<EasyMerchantMappingResponse> saveMerchantMapping(
      @RequestBody EasyMerchantMappingSaveRequest request) {
    request.validate();
    var mapping = accountingService.saveMerchantMapping(
        request.businessId(), request.rawMerchant(), request.accountId(), request.adminId());
    return ResponseEntity.ok(mapping);
  }

  /**
   * List all merchant mappings for a business (Phase 5A.2).
   */
  @GetMapping("/merchant-mappings")
  public ResponseEntity<List<EasyMerchantMappingResponse>> listMerchantMappings(
      @RequestParam Long businessId) {
    return ResponseEntity.ok(accountingService.listMerchantMappings(businessId));
  }

  /**
   * Deactivate a merchant mapping (Phase 5A.2).
   */
  @DeleteMapping("/merchant-mappings/{mappingId}")
  public ResponseEntity<Void> deactivateMerchantMapping(
      @PathVariable Long mappingId, @RequestParam String adminId) {
    accountingService.deactivateMerchantMapping(mappingId, adminId);
    return ResponseEntity.ok().build();
  }

  /**
   * Reactivate a merchant mapping (Phase 5A.2).
   */
  @PutMapping("/merchant-mappings/{mappingId}/reactivate")
  public ResponseEntity<Void> reactivateMerchantMapping(
      @PathVariable Long mappingId, @RequestParam String adminId) {
    accountingService.reactivateMerchantMapping(mappingId, adminId);
    return ResponseEntity.ok().build();
  }
}
