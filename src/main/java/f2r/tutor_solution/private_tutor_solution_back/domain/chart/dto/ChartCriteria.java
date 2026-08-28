package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import java.util.List;

/**
 * BuildIt AI 평가 기준 공개 (홈페이지·/chart 의 "AI는 무엇을 보고 평가하나요?" 영역).
 * 여기 담긴 항목·가중치는 {@link f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService}
 * 의 실제 {@code score()} 산식과 일치해야 한다. 사용자-facing 문구는 코드 의미를 왜곡하지 않는 범위에서 한국어로 표현한다.
 */
public record ChartCriteria(
    String criteriaVersion,
    int maxScore,
    List<Criterion> criteria,
    List<String> notEvaluated,
    String rankingDisclaimer,
    String scoreDisclaimer
) {
  public record Criterion(String key, String label, int weight, String description) {}
}
