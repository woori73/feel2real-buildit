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
 * 수집 자료 장부 반영 승인 API — P1/P3.
 *
 * <p>ADMIN·TUTOR 권한 필요. 테넌트 격리는 서비스 레이어에서 처리한다.
 * 승인 전에는 장부에 반영되지 않는다.
 *
 * <p>P3 엔드포인트:
 * <ul>
 *   <li>{@code POST /{approvalId}/approve-and-commit} — 승인 + 장부 반영</li>
 *   <li>{@code POST /{approvalId}/retry-commit} — 반영 실패 시 재시도</li>
 * </ul>
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

  /**
   * 승인 + 장부 반영 (P3).
   * 반영 실패 시 COMMIT_FAILED로 남으며 정상 완료로 표시하지 않는다.
   */
  @PostMapping("/{approvalId}/approve-and-commit")
  @PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
  public ResponseEntity<Map<String, Object>> approveAndCommit(
      @PathVariable Long approvalId,
      @RequestParam Long businessId,
      @RequestBody(required = false) Map<String, String> body,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    String decidedBy = body != null ? body.getOrDefault("decidedBy", "") : "";
    String note = body != null ? body.getOrDefault("note", "") : "";
    EasyCollectionApproval approval =
        approvalService.approveAndCommit(businessId, approvalId, decidedBy, note, principal);
    return ResponseEntity.ok(toResponse(approval));
  }

  /**
   * 장부 반영 재시도 (P3) — COMMIT_FAILED 상태에서만 동작한다.
   * 이미 COMMITTED된 건은 재반영하지 않는다.
   */
  @PostMapping("/{approvalId}/retry-commit")
  @PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
  public ResponseEntity<Map<String, Object>> retryCommit(
      @PathVariable Long approvalId,
      @RequestParam Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    EasyCollectionApproval approval =
        approvalService.retryCommit(businessId, approvalId, principal);
    return ResponseEntity.ok(toResponse(approval));
  }

  private Map<String, Object> toResponse(EasyCollectionApproval approval) {
    return Map.of(
        "approvalId", approval.getApprovalId(),
        "runId", approval.getRun().getRunId(),
        "businessId", approval.getBusiness().getBusinessId(),
        "status", approval.getStatus().name(),
        "requestedAt", approval.getRequestedAt().toString(),
        "decidedAt", approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : "",
        "committedAt", approval.getCommittedAt() != null ? approval.getCommittedAt().toString() : "",
        "commitError", approval.getCommitError() != null ? approval.getCommitError() : "");
  }
}
