package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartCandidateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartDiscoveryRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartGeminiKeyRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartSettingsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartZoneResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartDiscoveryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartGeminiClient;
import java.util.Arrays;
import java.util.List;
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

  private ChartSettingsResponse settingsResponse() {
    return ChartSettingsResponse.from(chartDiscoveryService.getOrCreateSettings(), chartGeminiClient.isReady(), chartGeminiClient.apiKeySource(), chartGeminiClient.provider(), chartGeminiClient.model());
  }
}
