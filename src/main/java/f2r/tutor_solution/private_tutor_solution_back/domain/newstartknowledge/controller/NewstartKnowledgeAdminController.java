package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartEmpiricalCaseService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeService;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/newstart/wiki")
public class NewstartKnowledgeAdminController {

    private final NewstartKnowledgeService knowledgeService;
    private final NewstartEmpiricalCaseService empiricalCaseService;

    // Knowledge Item CRUD
    @GetMapping
    public ResponseEntity<Page<NewstartKnowledgeItemResponse>> search(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(knowledgeService.search(q, sourceType, category, status, page, size, sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NewstartKnowledgeItemResponse> getById(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id) {
        return knowledgeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public ResponseEntity<List<NewstartKnowledgeItemResponse>> getActive(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(knowledgeService.findAllActive());
    }

    @GetMapping("/source-type/{sourceType}")
    public ResponseEntity<List<NewstartKnowledgeItemResponse>> getBySourceType(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable String sourceType) {
        return ResponseEntity.ok(knowledgeService.findBySourceType(sourceType));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<NewstartKnowledgeItemResponse>> getByCategory(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable String category) {
        return ResponseEntity.ok(knowledgeService.findByCategory(category));
    }

    @GetMapping("/stats")
    public ResponseEntity<NewstartKnowledgeStatsResponse> getStats(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(knowledgeService.getStats());
    }

    @PostMapping
    public ResponseEntity<NewstartKnowledgeItemResponse> create(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @Valid @RequestBody NewstartKnowledgeItemCreateRequest req) {
        return ResponseEntity.ok(knowledgeService.create(req, principal.loginId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NewstartKnowledgeItemResponse> update(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody NewstartKnowledgeItemUpdateRequest req) {
        return ResponseEntity.ok(knowledgeService.update(id, req, principal.loginId()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String reason) {
        knowledgeService.changeStatus(id, status, principal.loginId(), reason);
        return ResponseEntity.ok().build();
    }

    // ── ADMIN Review Workflow ──────────────────────────────────────────
    // REVIEW 목록 → 상세(경고+provenance) → 승인/유지/보관.
    // ACTIVE 전환은 approveReview 명시적 action만 허용. 자동 승인 금지.

    /** REVIEW 검토함 목록. */
    @GetMapping("/review")
    public ResponseEntity<Page<NewstartKnowledgeItemResponse>> getReviewQueue(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(knowledgeService.getReviewQueue(page, size, sort));
    }

    /** REVIEW 상세: title/source type/authority/effective/source URL/evidence/category/conflict/UNKNOWN/related/provenance. */
    @GetMapping("/review/{id}")
    public ResponseEntity<NewstartReviewDetailResponse> getReviewDetail(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(knowledgeService.getReviewDetail(id));
    }

    /** REVIEW → ACTIVE 명시적 승인. 경고가 있으면 confirmed=true + reason 필수. */
    @PostMapping("/review/{id}/approve")
    public ResponseEntity<?> approveReview(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) NewstartReviewActionRequest req) {
        String reason = req != null ? req.getReason() : null;
        boolean confirmed = req != null && Boolean.TRUE.equals(req.getConfirmed());
        return ResponseEntity.ok(knowledgeService.approveReview(id, principal.loginId(), reason, confirmed));
    }

    /** REVIEW 유지 (보류). 상태는 그대로, 검토 메모만 이력에 남긴다. */
    @PostMapping("/review/{id}/keep")
    public ResponseEntity<NewstartKnowledgeItemResponse> keepReview(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) NewstartReviewActionRequest req) {
        String reason = req != null ? req.getReason() : null;
        return ResponseEntity.ok(knowledgeService.keepReview(id, principal.loginId(), reason));
    }

    /** REVIEW/ACTIVE → ARCHIVED (반려/보관). reason 필수. */
    @PostMapping("/review/{id}/archive")
    public ResponseEntity<NewstartKnowledgeItemResponse> archiveReview(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) NewstartReviewActionRequest req) {
        String reason = req != null ? req.getReason() : null;
        return ResponseEntity.ok(knowledgeService.archiveReview(id, principal.loginId(), reason));
    }

    /** 상태 변경 이력 = provenance 조회. source URL은 상세 응답에 포함. */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<NewstartStatusHistoryResponse>> getHistory(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(knowledgeService.getHistory(id));
    }

    /**
     * 삭제는 하드 삭제가 아니라 ARCHIVED 처리로 수행한다 (provenance 보존).
     * 기존 UI의 삭제 버튼과 호환.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id) {
        try {
            knowledgeService.archiveReview(id, principal.loginId(), "ADMIN 삭제 요청 → ARCHIVED 처리");
        } catch (IllegalStateException e) {
            // REVIEW/ACTIVE가 아니면 DRAFT 경유로 보관한다.
            knowledgeService.changeStatus(id, "ARCHIVED", principal.loginId(), "ADMIN 삭제 요청 → ARCHIVED 처리");
        }
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(NewstartKnowledgeService.ReviewConfirmationRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleConfirmationRequired(
            NewstartKnowledgeService.ReviewConfirmationRequiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "CONFIRMATION_REQUIRED",
                "message", e.getMessage(),
                "knowledgeId", e.getKnowledgeId(),
                "warnings", e.getWarnings()));
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleBadReviewRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "REVIEW_REQUEST_REJECTED",
                "message", e.getMessage() == null ? "요청이 거부되었습니다" : e.getMessage()));
    }

    // Empirical Cases
    @GetMapping("/cases")
    public ResponseEntity<NewstartEmpiricalCaseListResponse> searchCases(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(required = false) String articleId,
            @RequestParam(required = false) String authorNickname,
            @RequestParam(required = false) String finalReductionRate,
            @RequestParam(required = false) String householdType,
            @RequestParam(required = false) Integer minAge,
            @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) Long minDebtTotal,
            @RequestParam(required = false) Long maxDebtTotal,
            @RequestParam(required = false) Long minIncome2025,
            @RequestParam(required = false) Long maxIncome2025,
            @RequestParam(required = false) String householdTypeFilter,
            @RequestParam(required = false) String hasFinalReductionRate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {

        NewstartEmpiricalCaseSearchRequest req = NewstartEmpiricalCaseSearchRequest.builder()
                .articleId(articleId)
                .authorNickname(authorNickname)
                .finalReductionRate(finalReductionRate)
                .householdType(householdType)
                .minAge(minAge)
                .maxAge(maxAge)
                .minDebtTotal(minDebtTotal)
                .maxDebtTotal(maxDebtTotal)
                .minIncome2025(minIncome2025)
                .maxIncome2025(maxIncome2025)
                .householdTypeFilter(householdTypeFilter)
                .hasFinalReductionRate(hasFinalReductionRate)
                .page(page)
                .size(size)
                .sort(sort)
                .build();

        return ResponseEntity.ok(empiricalCaseService.search(req));
    }

    @GetMapping("/cases/{articleId}")
    public ResponseEntity<NewstartEmpiricalCaseResponse> getCaseByArticleId(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable String articleId) {
        return empiricalCaseService.findByArticleId(articleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cases/with-actual-rate")
    public ResponseEntity<List<NewstartEmpiricalCaseResponse>> getCasesWithActualRate(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(empiricalCaseService.findWithActualRate());
    }

    @GetMapping("/cases/by-household/{householdType}")
    public ResponseEntity<List<NewstartEmpiricalCaseResponse>> getCasesByHouseholdType(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable String householdType) {
        return ResponseEntity.ok(empiricalCaseService.findByHouseholdType(householdType));
    }

    @GetMapping("/cases/stats")
    public ResponseEntity<NewstartEmpiricalCaseStatsResponse> getCaseStats(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(empiricalCaseService.getStats());
    }
}