package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCuratedReviewIngestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartDataIngestionService;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/newstart/wiki")
public class NewstartIngestionAdminController {

    private final NewstartDataIngestionService ingestionService;
    private final NewstartCuratedReviewIngestService curatedReviewIngestService;

    @PostMapping("/ingest/cases")
    public ResponseEntity<NewstartDataIngestionService.IngestionResult> ingestCases(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "data/newstart-cafe/gamian/articles") String articlesDir) {
        return ResponseEntity.ok(ingestionService.ingestEmpiricalCases(articlesDir));
    }

    @PostMapping("/ingest/generate-rules")
    public ResponseEntity<Integer> generateRules(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        int created = ingestionService.generateKnowledgeFromCases();
        return ResponseEntity.ok(created);
    }

    /** Agent3 큐레이션(EBOOK section + gamian CASE) REVIEW 적재 — ACTIVE 승격 없음. */
    @PostMapping("/ingest/curated-review")
    public ResponseEntity<NewstartCuratedReviewIngestService.CuratedIngestReport> ingestCuratedReview(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "docs/newstart/ebook/ebook_sections.json") String ebookSections,
            @RequestParam(defaultValue = "data/newstart-cafe/gamian/articles") String gamianDir) {
        return ResponseEntity.ok(curatedReviewIngestService.ingestCuratedReview(
                Paths.get(ebookSections), Paths.get(gamianDir)));
    }
}