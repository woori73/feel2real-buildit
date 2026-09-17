package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

/** Easy "AI가 확인할 장부" 개수 — 대시보드 카드용. */
public record EasyReviewSummaryResponse(
    Long businessId,
    String businessName,
    long unclassified,
    long evidence,
    long business,
    long withholding,
    long duplicateSuspect,
    long importUnconfirmed,
    long totalOpen
) {
}
