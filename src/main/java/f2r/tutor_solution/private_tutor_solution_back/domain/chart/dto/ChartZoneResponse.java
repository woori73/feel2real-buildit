package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import java.util.List;

public record ChartZoneResponse(
    String id,
    String name,
    List<String> searchAreas,
    List<String> schoolTerms,
    boolean allowed
) {
  public static ChartZoneResponse from(ChartDiscoveryZone zone) {
    return new ChartZoneResponse(zone.getId(), zone.getLabel(), zone.getSearchAreas(), zone.getSchoolTerms(), zone.isEnabledForDiscovery());
  }
}
