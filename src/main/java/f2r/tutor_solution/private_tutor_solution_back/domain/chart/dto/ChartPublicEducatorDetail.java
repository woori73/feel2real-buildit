package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공개 교육자 상세. DB에 실제로 확인된 사실만 담으며, 없는 값은 {@code null}/빈 배열로 둔다(생성·추측 금지).
 *
 * <p>이 화면은 검색·발견용이다. BuildIt Score·TOP100 순위는 담지 않는다({@code /api/chart/publication/current}
 * 와 분리). {@code researchPending} 는 아직 평가 근거가 충분히 확인되지 않았음을 뜻한다.
 */
public record ChartPublicEducatorDetail(
    long id,
    String name,
    String role,
    String academyName,
    String province,
    String cityDistrict,
    String activityRegion,
    String identitySummary,
    String educationPhilosophy,
    String curriculum,
    String distinctiveTeaching,
    String recentActivity,
    List<String> strengths,
    List<String> improvementSuggestions,
    ChartPublicOfficialChannels officialChannels,
    String profileImageUrl,
    List<ChartPublicEvidence> evidence,
    String discoveryReason,
    LocalDateTime lastResearchedAt,
    String eligibilityStatus,
    boolean researchPending,
    String disclaimer
) {}
