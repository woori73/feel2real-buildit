package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartDataIngestionService;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/newstart/wiki")
public class NewstartIngestionAdminController {

    private final NewstartDataIngestionService ingestionService;

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
}