package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/**
 * Easy 신고자료 대조 — 장부 정상 계산 vs 기준자료 독립 비교.
 * 신고 금액에 맞추는 역산 금지. 설명 불가 차이는 NEEDS_REVIEW/UNKNOWN으로 둔다.
 */
public record EasyFilingCheckResponse(
    Long businessId,
    String businessName,
    int fiscalYear,
    long ledgerRevenue,
    long ledgerExpense,
    long ledgerProfit,
    Long refRevenue,
    Long refExpense,
    Long refIncome,
    String refSource,
    List<EasyFilingLine> lines,
    String note
) {
  public record EasyFilingLine(
      String label,
      long ledger,
      Long reference,
      Long diff,
      String cause
  ) {
  }
}
