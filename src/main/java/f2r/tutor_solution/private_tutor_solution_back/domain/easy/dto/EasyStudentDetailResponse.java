package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/**
 * 학생 상세 — 수납 합계 + 월별 내역 + 소속 사업체(수납 이력 기준, 중복 제거).
 * 학생 자체에 사업체를 두지 않고 수납에서 파생한다.
 */
public record EasyStudentDetailResponse(
    EasyStudentResponse student,
    List<EasyPaymentResponse> payments,
    List<String> businesses
) {
}
