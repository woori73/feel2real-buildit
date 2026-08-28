package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartCriteria;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorRanking;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRankingList;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRegionDirectory;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartZoneResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartEducatorSearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartPublicService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartRankingService;
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
  private final ChartRankingService chartRankingService;

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

  // ----- PR-C: 지역/전국 Ranking (read-only, 현재 publication 에서 파생, EVALUATABLE 만) -----

  /**
   * BuildIt Ranking 목록. {@code province} 없으면 전국 순위 상위 {@code limit}(기본 100 = TOP100),
   * {@code province}(+선택 {@code cityDistrict})가 있으면 해당 지역 순위. 동일한 BuildIt Score 사용.
   */
  @GetMapping("/rankings")
  public ChartRankingList rankings(
      @RequestParam(required = false) String province,
      @RequestParam(required = false) String cityDistrict,
      @RequestParam(required = false) Integer limit) {
    return province == null || province.isBlank()
        ? chartRankingService.nationwide(limit)
        : chartRankingService.region(province, cityDistrict);
  }

  /** 교육자 1명의 BuildIt Ranking. 순위 대상이 아니면 {@code ranked:false}. */
  @GetMapping("/rankings/{id:\\d+}")
  public ChartEducatorRanking educatorRanking(@PathVariable long id) {
    return chartRankingService.forEducator(id);
  }

  /** BuildIt AI 평가 기준(항목·가중치는 실제 Score 산식과 일치). */
  @GetMapping("/criteria")
  public ChartCriteria criteria() {
    return chartRankingService.criteria();
  }

  /** 공개 검색 가능한(= BuildIt 이 발견한) 지역 목록 + 인원. 지역 선택 UI 용. */
  @GetMapping("/regions")
  public ChartRegionDirectory regions() {
    return chartRankingService.regions();
  }
}
