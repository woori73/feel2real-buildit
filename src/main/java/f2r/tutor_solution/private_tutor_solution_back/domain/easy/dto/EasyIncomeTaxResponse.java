package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/**
 * Easy 종합소득세 준비 — 사업장별 장부는 별도 유지, 이 조회에서만 합산한다.
 * 세무조정·공제·기납부세액 데이터가 없으므로 결정세액을 표시하지 않는다 (PREPARING).
 */
public record EasyIncomeTaxResponse(
    int year,
    List<EasyIncomeTaxSlice> slices,
    long combinedRevenue,
    long combinedExpense,
    long combinedProfit,
    String status,
    String note
) {
  public record EasyIncomeTaxSlice(
      Long businessId,
      String businessName,
      boolean primary,
      long revenue,
      long expense,
      long profit,
      Long refRevenue,
      String checkCause
  ) {
  }
}
