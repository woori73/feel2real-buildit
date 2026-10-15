package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import java.time.LocalDate;

/**
 * 공개 교육자 상세에 노출하는 근거(evidence) 한 건. 공개적으로 확인된 교육 관련 출처만 담는다.
 * 내부 판정 메모·해시·비공개 운영정보는 포함하지 않는다.
 */
public record ChartPublicEvidence(
    String contentType,
    String summary,
    String sourceUrl,
    LocalDate activityDate,
    String evidenceKind
) {}
