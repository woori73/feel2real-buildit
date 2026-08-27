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
    return service.completeRegion(regionId, request.eligibleCount(), request.rejectedCount(), request.note());
  }

  @PostMapping("/educators/search-existing")
  public List<Map<String, Object>> searchExisting(@RequestBody ExistingSearchRequest request) {
    return service.findExisting(request.name(), request.academyName(), request.province(), request.cityDistrict());
  }

  @PostMapping("/research-drafts")
  public Map<String, Object> saveResearchDraft(@RequestBody DraftRequest request) { return service.saveDraft(request); }

  @GetMapping("/scores/preview")
  public Map<String, Object> previewScore(@RequestParam long candidateId) { return service.previewScore(candidateId); }

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

  @PostMapping("/educators/{candidateId}/web-draft-requests")
  public Map<String, Object> requestWebDraft(@PathVariable long candidateId, @RequestBody WebDraftRequest request) {
    return service.requestWebDraft(candidateId, request.educatorConsentAt());
  }

  @PostMapping("/publications")
  public Map<String, Object> publish() { return service.publicationDisabled(); }

  public record ExistingSearchRequest(String name, String academyName, String province, String cityDistrict) {}
  public record ReviewBatchRequest(String regionId, List<Long> candidateIds, String summary) {}
  public record RegionCompletionRequest(int eligibleCount, int rejectedCount, String note) {}
  public record ReviewDecisionRequest(String decision, String note) {}
  public record WebDraftRequest(java.time.LocalDateTime educatorConsentAt) {}
}
