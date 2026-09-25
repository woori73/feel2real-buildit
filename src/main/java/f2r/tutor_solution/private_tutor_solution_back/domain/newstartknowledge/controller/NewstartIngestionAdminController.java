package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCuratedReviewIngestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartDataIngestionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartOfficialReviewIngestService;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.nio.file.Paths;
import java.util.List;
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
    private final NewstartOfficialReviewIngestService officialReviewIngestService;

    @PostMapping("/ingest/cases")
    public ResponseEntity<NewstartDataIngestionService.IngestionResult> ingestCases(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "data/newstart-cafe/gamian/articles") String articlesDir) {
        return ResponseEntity.ok(ingestionService.ingestEmpiricalCases(articlesDir));
    }

    /**
     * 8·9월 정합성 복구 import — repair_staging JSON 대상 articleId만 비파괴 병합(insert/fill-only).
     * ACTIVE 생성·승인 워크플로 우회 경로가 아니며, knowledge 테이블에는 접근하지 않는다.
     */
    @PostMapping("/ingest/cases/repair")
    public ResponseEntity<NewstartDataIngestionService.RepairIngestionResult> repairCases(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "data/newstart-cafe/repair_staging/articles") String articlesDir) {
        return ResponseEntity.ok(ingestionService.repairEmpiricalCases(articlesDir));
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

    /**
     * PHASE B 공식 자료(REVIEW) initial seed — research-official(비교 매트릭스·evidence·
     * concepts·ccrs)을 기존 NewstartOfficialReviewIngestService 계약 그대로 적재한다.
     * ACTIVE 생성 경로 없음(newItem은 전부 REVIEW 강제), official_updates 테이블은
     * 절대 접근하지 않는다. dryRun=true이면 수집·title 멱등 판정만 수행하고 쓰지 않는다.
     */
    @PostMapping("/ingest/official-review")
    public ResponseEntity<OfficialIngestResponse> ingestOfficialReview(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "docs/newstart/research-official") String officialDir,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        if (dryRun) {
            NewstartOfficialReviewIngestService.OfficialIngestPlan plan =
                    officialReviewIngestService.planOfficialReview(Paths.get(officialDir));
            return ResponseEntity.ok(new OfficialIngestResponse(true, plan.sourceCount(),
                    plan.wouldCreate(), plan.wouldSkip(), plan.nonReviewStatus(),
                    plan.relationCandidates(), plan.errors().size(), plan.activeCreated(),
                    plan.errors()));
        }
        NewstartOfficialReviewIngestService.IngestReport report =
                officialReviewIngestService.ingestOfficialReview(Paths.get(officialDir));
        return ResponseEntity.ok(new OfficialIngestResponse(false,
                report.created() + report.skipped(), report.created(), report.skipped(),
                report.downgraded(), report.relations(), report.errors().size(), 0,
                report.errors()));
    }

    /**
     * @param sourceCount   수집된 source item 수 (dryRun이면 계획 총계)
     * @param created       dryRun이면 생성 예정(wouldCreate)
     * @param skipped       dryRun이면 스킵 예정(wouldSkip)
     * @param conflictReview rawStatus가 REVIEW가 아니던 항목(dry-run이면 그렇게 처리될 수) —
     *                       저장 시 항상 REVIEW로 강등 적재된다
     * @param relations     CONTRADICTS relation 생성 수(dryRun이면 생성 후보 수)
     * @param failed        collector/저장 오류 수
     * @param activeCreated 항상 0 — ACTIVE 전환은 admin 승인 워크플로 전용
     */
    public record OfficialIngestResponse(
            boolean dryRun, int sourceCount, int created, int skipped, int conflictReview,
            int relations, int failed, int activeCreated, List<String> errors) {}
}