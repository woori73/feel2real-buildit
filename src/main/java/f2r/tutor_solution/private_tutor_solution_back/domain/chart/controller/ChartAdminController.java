package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartCandidateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartDiscoveryRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartGeminiKeyRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartSettingsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartZoneResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartCandidateEnrichmentService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartCandidateEnrichmentService.EnrichmentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartDiscoveryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartGeminiClient;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/chart")
@RequiredArgsConstructor
public class ChartAdminController {

  private final ChartDiscoveryService chartDiscoveryService;
  private final ChartGeminiClient chartGeminiClient;
  private final ChartCandidateEnrichmentService chartCandidateEnrichmentService;

  @GetMapping("/zones")
  public List<ChartZoneResponse> zones() {
    return Arrays.stream(ChartDiscoveryZone.values()).map(ChartZoneResponse::from).toList();
  }

  @GetMapping("/settings")
  public ChartSettingsResponse settings() {
    return settingsResponse();
  }

  @PutMapping("/settings")
  public ChartSettingsResponse updateSettings(@RequestBody ChartSettingsUpdateRequest request) {
    chartDiscoveryService.updateSettings(
        request.searchZone(), request.includeActivities(), request.excludedTargets(),
        request.candidateLimit() == null ? 10 : request.candidateLimit(),
        request.deepSearchEnabled() == null || request.deepSearchEnabled(),
        request.extraInstruction());
    return settingsResponse();
  }

  @PostMapping("/settings/reset")
  public ChartSettingsResponse resetSettings() {
    chartDiscoveryService.restoreDefaultSettings();
    return settingsResponse();
  }

  @PostMapping("/gemini-key")
  public ChartSettingsResponse setGeminiKey(@RequestBody ChartGeminiKeyRequest request) {
    try {
      chartGeminiClient.setRuntimeApiKey(request.provider(), request.apiKey());
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
    }
    return settingsResponse();
  }

  @PostMapping("/gemini-key/clear")
  public ChartSettingsResponse clearGeminiKey() {
    chartGeminiClient.clearRuntimeApiKey();
    return settingsResponse();
  }

  @PostMapping("/llm-key")
  public ChartSettingsResponse setLlmKey(@RequestBody ChartGeminiKeyRequest request) {
    return setGeminiKey(request);
  }

  @PostMapping("/llm-key/clear")
  public ChartSettingsResponse clearLlmKey() {
    return clearGeminiKey();
  }

  @GetMapping("/candidates")
  public List<ChartCandidateResponse> candidates(@RequestParam(defaultValue = "apgujeong-sinsa") String zoneId) {
    return chartDiscoveryService.listCandidates(zoneId);
  }

  @PostMapping("/discovery/run")
  public ChartDiscoveryRunResponse runDiscovery() {
    return chartDiscoveryService.runDiscovery();
  }

  @PostMapping("/candidates/{candidateId}/approve")
  public ChartCandidateResponse approve(@PathVariable Long candidateId) {
    return chartDiscoveryService.approve(candidateId);
  }

  @PostMapping("/candidates/{candidateId}/reject")
  public ChartCandidateResponse reject(@PathVariable Long candidateId) {
    return chartDiscoveryService.reject(candidateId);
  }

  /**
   * 이미 저장된 교육자(chart_candidates)의 Educator Intelligence 보완.
   *
   * <p>신규 발굴 저장(saveDraft, INSERT)과 분리된 <b>기존 candidate_id 전용 UPDATE</b>다. 교육철학·커리큘럼·
   * 교육방식·최근활동 요약, 강점/개선제안, 그리고 원 출처 Evidence 추가만 가능하다. Score·Rank·
   * eligibility_status·publication 상태·excluded_from_chart·legacy_source·identity(이름·학원·지역)는
   * 변경하지 않는다(요청 record 에 해당 필드가 없음). 빈 요청이나 존재하지 않는 candidate_id 는 오류.
   */
  @PostMapping("/candidates/{candidateId}/enrichment")
  public Map<String, Object> enrichCandidate(@PathVariable long candidateId,
      @RequestBody EnrichmentRequest request) {
    return chartCandidateEnrichmentService.enrich(candidateId, request);
  }

  private ChartSettingsResponse settingsResponse() {
    return ChartSettingsResponse.from(chartDiscoveryService.getOrCreateSettings(), chartGeminiClient.isReady(), chartGeminiClient.apiKeySource(), chartGeminiClient.provider(), chartGeminiClient.model());
  }
}
