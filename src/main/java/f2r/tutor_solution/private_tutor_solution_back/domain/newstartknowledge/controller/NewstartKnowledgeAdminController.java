package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartEmpiricalCaseService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeService;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(knowledgeService.search(q, sourceType, category, status, page, 20, sort));
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
    public ResponseEntity<StatsResponse> getStats(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(knowledgeService.getStats());
    }

    @PostMapping
    public ResponseEntity<NewstartKnowledgeItemResponse> create(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @Valid @RequestBody NewstartKnowledgeItemCreateRequest req) {
        return ResponseEntity.ok(knowledgeService.create(req, principal.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NewstartKnowledgeItemResponse> update(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody NewstartKnowledgeItemUpdateRequest req) {
        return ResponseEntity.ok(knowledgeService.update(id, req, principal.getUsername()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String reason) {
        knowledgeService.changeStatus(id, status, principal.getUsername(), reason);
        return ResponseEntity.ok().build();
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