package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartZoneResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartEducatorSearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartPublicService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
public class ChartPublicController {

  private final ChartPublicService chartPublicService;
  private final ChartEducatorSearchService chartEducatorSearchService;

  @GetMapping("/zones")
  public List<ChartZoneResponse> zones() {
    return Arrays.stream(ChartDiscoveryZone.values()).map(ChartZoneResponse::from).toList();
  }

  @GetMapping("/educators")
  public List<ChartEducatorResponse> educators() {
    return chartPublicService.defaultTopEducators();
  }

  /**
   * 전국 초·중·고 영어교육자 공개 검색. TOP100 publication 과 분리된 교육자 아카이브 검색이다.
   * {@code EVALUATABLE} · {@code RESEARCH_PENDING} 모두 노출하며 Score·순위는 반환하지 않는다.
   */
  @GetMapping("/educators/search")
  public ChartPublicEducatorPage searchEducators(
      @RequestParam(required = false) String province,
      @RequestParam(required = false) String cityDistrict,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return chartEducatorSearchService.search(province, cityDistrict, q, status, page, size);
  }

  /** 공개 교육자 상세. 확인된 사실만 반환하며 Score·순위는 담지 않는다. 비공개 후보는 404. */
  @GetMapping("/educators/{id:\\d+}")
  public ChartPublicEducatorDetail educatorDetail(@PathVariable long id) {
    return chartEducatorSearchService.getById(id);
  }

  @GetMapping("/publication/current")
  public Map<String, Object> currentPublication() { return chartPublicService.currentPublication(); }

  @GetMapping("/zones/{zoneId}/educators")
  public List<ChartEducatorResponse> educatorsByZone(@PathVariable String zoneId) {
    return chartPublicService.topEducators(zoneId);
  }
}
