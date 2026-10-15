package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.DraftRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/chart/tools")
public class ChartSiteToolController {

  private final ChartSiteToolService service;

  public ChartSiteToolController(ChartSiteToolService service) {
    this.service = service;
  }

  @GetMapping("/context")
  public Map<String, Object> getChartContext() { return service.context(); }

  @GetMapping("/regions/next")
  public List<Map<String, Object>> getNextRegions(@RequestParam(defaultValue = "1") int limit) {
    return service.nextRegions(limit);
  }

  @PostMapping("/regions/{regionId}/claim")
  public Map<String, Object> claimRegion(@PathVariable String regionId) { return service.claimRegion(regionId); }

  @PostMapping("/regions/{regionId}/complete")
  public Map<String, Object> completeRegion(@PathVariable String regionId, @RequestBody RegionCompletionRequest request) {
    return service.completeRegion(regionId, request.selectedCandidateIds(), request.selectedCount(), request.note());
  }

  @PostMapping("/educators/search-existing")
  public List<Map<String, Object>> searchExisting(@RequestBody ExistingSearchRequest request) {
    return service.findExisting(request.name(), request.academyName(), request.province(), request.cityDistrict());
  }

  @PostMapping("/research-drafts")
  public Map<String, Object> saveResearchDraft(@RequestBody DraftRequest request) { return service.saveDraft(request); }

  @GetMapping("/scores/preview")
  public Map<String, Object> previewScore(@RequestParam long candidateId) { return service.previewScore(candidateId); }

  /**
   * 재채점이 필요한 후보 목록(읽기 전용). 최신 profile/evidence 변경이 최신 Score snapshot보다
   * 새로울 때 review → snapshot → publication 후속 처리 대상이다. 아무 것도 변경하지 않는다.
   */
  @GetMapping("/scores/stale")
  public List<Map<String, Object>> staleScores() { return service.rescoreRequired(); }

  /**
   * RESEARCH_PENDING 후보를 현재 저장 데이터로 재평가한다. 최초 판정과 동일한 기준으로 PASS일 때만
   * EVALUATABLE로 전환하고, FAIL이면 유지한다. 기본 {@code dryRun=true}(조회만, 쓰기 없음).
   */
  @PostMapping("/candidates/{candidateId}/reevaluate")
  public Map<String, Object> reevaluateCandidate(@PathVariable long candidateId,
      @RequestParam(defaultValue = "true") boolean dryRun) {
    return service.reevaluateEligibility(candidateId, dryRun);
  }

  @PostMapping("/review-batches")
  public Map<String, Object> submitReview(@RequestBody ReviewBatchRequest request) {
    return service.submitReview(request.regionId(), request.candidateIds(), request.summary());
  }

  @GetMapping("/review-batches")
  public List<Map<String, Object>> pendingReviewBatches() { return service.pendingReviewBatches(); }

  @PostMapping("/review-batches/{reviewBatchId}/decision")
  public Map<String, Object> decideReview(@PathVariable long reviewBatchId, @RequestBody ReviewDecisionRequest request) {
    return service.reviewDecision(reviewBatchId, request.decision(), request.note());
  }

  /**
   * 이미 Draft/Score/Batch 가 있는 조사(예: 분당 Review Batch 1)를 새 자동발행 코드에서 publication
   * 단계부터 resume 한다. AI/WebMCP 도구가 아니라 서버/관리자 트리거용이며 새 Draft/Score 를 만들지 않는다.
   */
  @PostMapping("/review-batches/{reviewBatchId}/finalize")
  public Map<String, Object> finalizeReviewBatch(@PathVariable long reviewBatchId) {
    return service.finalizeReviewBatch(reviewBatchId);
  }

  @PostMapping("/educators/{candidateId}/web-draft-requests")
  public Map<String, Object> requestWebDraft(@PathVariable long candidateId, @RequestBody WebDraftRequest request) {
    return service.requestWebDraft(candidateId, request.educatorConsentAt());
  }

  @PostMapping("/publications")
  public Map<String, Object> publish() { return service.publicationDisabled(); }

  /**
   * 현재까지 선정된 교육자로 전국 누적 publication 을 재생성한다(서버/관리자 트리거). 지역 조사와
   * 무관하며 AI/WebMCP 명령이 아니다. Legacy import 반영·사후 공개 제외 후 갱신 등에 사용.
   */
  @PostMapping("/publications/rebuild")
  public Map<String, Object> rebuildPublication() { return service.rebuildNationwidePublication(); }

  /** review batch 하나에 연결된 candidate/score snapshot/publication entry 조회(사고 조사용, 읽기 전용). */
  @GetMapping("/review-batches/{reviewBatchId}")
  public Map<String, Object> inspectReviewBatch(@PathVariable long reviewBatchId) {
    return service.inspectReviewBatch(reviewBatchId);
  }

  /**
   * 잘못 발행된 review batch 를 안전하게 되돌린다: 해당 batch 의 publication entry·score snapshot 만
   * 제거하고 batch 를 REVERTED 로 표시한 뒤 전국 publication 을 재생성한다. candidate 데이터는 보존한다.
   */
  @PostMapping("/review-batches/{reviewBatchId}/revert")
  public Map<String, Object> revertReviewBatch(@PathVariable long reviewBatchId) {
    return service.revertReviewBatch(reviewBatchId);
  }

  public record ExistingSearchRequest(String name, String academyName, String province, String cityDistrict) {}
  public record ReviewBatchRequest(String regionId, List<Long> candidateIds, String summary) {}
  public record RegionCompletionRequest(List<Long> selectedCandidateIds, Integer selectedCount, Integer rejectedCount, String note) {}
  public record ReviewDecisionRequest(String decision, String note) {}
  public record WebDraftRequest(java.time.LocalDateTime educatorConsentAt) {}
}
