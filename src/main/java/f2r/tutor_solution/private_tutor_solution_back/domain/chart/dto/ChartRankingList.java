package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import java.util.List;

/**
 * BuildIt Ranking 목록. {@code scope} 이 {@code NATIONWIDE} 면 전국 순위 상위 {@code limit}(기본 100 = TOP100 큐레이션 대상),
 * {@code REGION} 이면 해당 지역의 순위 목록이다. 어느 쪽이든 순위 산정에 쓰는 BuildIt Score 는 동일하다.
 *
 * <p>이 순위는 TOP100 publication 과 독립적으로 계산된다 — publication id 를 담지 않는다.
 */
public record ChartRankingList(
    String scope,
    String province,
    String cityDistrict,
    String criteriaVersion,
    int total,
    List<ChartRankedEducator> educators,
    String disclaimer
) {}
