package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

/**
 * BuildIt Ranking 목록의 교육자 1명. EVALUATABLE + 유효 BuildIt Score 를 가진(= 현재 publication 에 포함된)
 * 교육자만 여기에 등장한다. 개인 연락처·내부 조사 필드는 담지 않는다.
 *
 * <p>{@code buildItScore} 는 기존 {@code buildit-chart-v1} 산식의 total 이며 이 PR 에서 재계산하지 않는다.
 * {@code overallRank}/{@code regionRank} 는 동일한 Score 로 각각 전국·지역 안에서의 위치를 나타낸다.
 */
public record ChartRankedEducator(
    long id,
    String name,
    String role,
    String academyName,
    String province,
    String cityDistrict,
    String activityRegion,
    String identitySummary,
    String educationPhilosophy,
    String profileImageUrl,
    int buildItScore,
    int overallRank,
    int overallTotal,
    int regionRank,
    int regionTotal,
    boolean top100
) {}
