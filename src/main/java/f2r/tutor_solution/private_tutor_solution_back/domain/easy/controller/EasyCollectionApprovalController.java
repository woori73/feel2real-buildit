package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionApprovalService;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수집 자료 장부 반영 승인 API — P1.
 *
 * <p>ADMIN·TUTOR 권한 필요. 테넌트 격리는 서비스 레이어에서 처리한다.
 * 승인 전에는 장부에 반영되지 않는다.
 */
@RestController
@RequestMapping("/api/admin/easy/collection-approvals")
@RequiredArgsConstructor
public class EasyCollectionApprovalController {

  private final EasyCollectionApprovalService approvalService;

  @PostMapping("/request")
  @PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
  public ResponseEntity<Map<String, Object>> requestApproval(
      @RequestParam Long businessId,
      @RequestParam Long runId,
      @RequestParam(required = false) String requestedBy,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    EasyCollectionApproval approval =
        approvalService.requestApproval(businessId, runId, requestedBy, principal);
    return ResponseEntity.ok(toResponse(approval));
  }

  @PostMapping("/{approvalId}/approve")
  @PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
  public ResponseEntity<Map<String, Object>> approve(
      @PathVariable Long approvalId,
      @RequestParam Long businessId,
      @RequestBody(required = false) Map<String, String> body,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    String decidedBy = body != null ? body.getOrDefault("decidedBy", "") : "";
    String note = body != null ? body.getOrDefault("note", "") : "";
    EasyCollectionApproval approval =
        approvalService.approve(businessId, approvalId, decidedBy, note, principal);
    return ResponseEntity.ok(toResponse(approval));
  }

  @PostMapping("/{approvalId}/reject")
  @PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
  public ResponseEntity<Map<String, Object>> reject(
      @PathVariable Long approvalId,
      @RequestParam Long businessId,
      @RequestBody(required = false) Map<String, String> body,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    String decidedBy = body != null ? body.getOrDefault("decidedBy", "") : "";
    String note = body != null ? body.getOrDefault("note", "") : "";
    EasyCollectionApproval approval =
        approvalService.reject(businessId, approvalId, decidedBy, note, principal);
    return ResponseEntity.ok(toResponse(approval));
  }

  @GetMapping("/pending")
  @PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
  public ResponseEntity<List<Map<String, Object>>> listPending(
      @RequestParam Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    List<EasyCollectionApproval> approvals = approvalService.listPending(businessId, principal);
    return ResponseEntity.ok(approvals.stream()
        .map(this::toResponse)
        .collect(Collectors.toList()));
  }

  private Map<String, Object> toResponse(EasyCollectionApproval approval) {
    return Map.of(
        "approvalId", approval.getApprovalId(),
        "runId", approval.getRun().getRunId(),
        "businessId", approval.getBusiness().getBusinessId(),
        "status", approval.getStatus().name(),
        "requestedAt", approval.getRequestedAt().toString(),
        "decidedAt", approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : "");
  }
}
