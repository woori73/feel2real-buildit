package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import java.time.LocalDateTime;

/**
 * 공개 교육자 검색 결과 1건 (교육자 중심). 학원 directory가 아니라 "어떤 교육자인가"를 먼저 보여준다.
 *
 * <p>Score·TOP100 순위는 담지 않는다. {@code researchPending == true} 인 교육자는 아직 평가 근거가
 * 충분히 확인되지 않은 상태이며, 확인된 사실만 표시된다(폐기가 아니라 보강 대상).
 */
public record ChartPublicEducatorSummary(
    long id,
    String name,
    String role,
    String academyName,
    String province,
    String cityDistrict,
    String activityRegion,
    String identitySummary,
    String educationPhilosophy,
    String eligibilityStatus,
    boolean researchPending,
    String profileImageUrl,
    LocalDateTime lastResearchedAt
) {}
