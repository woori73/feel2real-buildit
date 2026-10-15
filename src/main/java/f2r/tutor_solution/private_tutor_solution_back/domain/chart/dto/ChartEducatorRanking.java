package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

/**
 * 교육자 1명의 BuildIt Ranking (상세 페이지용).
 *
 * <p>{@code ranked == false} 이면 이 교육자는 아직 순위 대상이 아니다(RESEARCH_PENDING 이거나 유효 Score
 * 미보유). 이 경우 순위·점수 필드는 모두 {@code null} 이며 UI 는 순위 영역을 표시하지 않는다.
 * {@code ranked} 여부와 무관하게 이 응답은 실제 수업 능력 순위가 아니라는 {@code disclaimer} 를 담는다.
 */
public record ChartEducatorRanking(
    long id,
    boolean ranked,
    Integer buildItScore,
    Integer overallRank,
    Integer overallTotal,
    Integer regionRank,
    Integer regionTotal,
    String province,
    String cityDistrict,
    Boolean top100,
    String criteriaVersion,
    String disclaimer
) {
  public static ChartEducatorRanking unranked(long id, String disclaimer) {
    return new ChartEducatorRanking(id, false, null, null, null, null, null, null, null, null, null, disclaimer);
  }
}
