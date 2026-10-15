package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

/**
 * Easy 검토 확인 요청 — 누락/예외 거래를 장부에 반영한다.
 * 사업장·계정·설명·증빙 상태를 선택해 확정 장부에 반영한다.
 */
public record EasyReviewResolveRequest(
    String resolution,
    Long businessId,
    Long accountId,
    String evidenceReview,
    String withholdingReview,
    Boolean ignore
) {
}
