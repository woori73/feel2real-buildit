package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import java.util.List;

/**
 * 공개 검색이 가능한(= BuildIt 이 이미 발견해 아카이브에 기록한) 지역 목록. 지역 선택 UI 를 채운다.
 * 새 지역 DB 체계가 아니라 {@code chart_candidates} 의 공개 대상 행에서 집계한 것이다.
 */
public record ChartRegionDirectory(List<Province> provinces) {

  public record Province(String province, int educatorCount, List<District> cityDistricts) {}

  public record District(String cityDistrict, int educatorCount, int evaluatableCount, int researchPendingCount) {}
}
