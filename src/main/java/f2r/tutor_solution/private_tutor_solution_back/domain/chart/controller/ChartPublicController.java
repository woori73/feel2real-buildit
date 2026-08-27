package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartZoneResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartPublicService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
public class ChartPublicController {

  private final ChartPublicService chartPublicService;

  @GetMapping("/zones")
  public List<ChartZoneResponse> zones() {
    return Arrays.stream(ChartDiscoveryZone.values()).map(ChartZoneResponse::from).toList();
  }

  @GetMapping("/educators")
  public List<ChartEducatorResponse> educators() {
    return chartPublicService.defaultTopEducators();
  }

  @GetMapping("/publication/current")
  public Map<String, Object> currentPublication() { return chartPublicService.currentPublication(); }

  @GetMapping("/zones/{zoneId}/educators")
  public List<ChartEducatorResponse> educatorsByZone(@PathVariable String zoneId) {
    return chartPublicService.topEducators(zoneId);
  }
}
