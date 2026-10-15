package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Easy 경영 대시보드 — 올해 1/1~오늘 vs 작년 동기 비교.
 * 세법 계산 없음. 종소세 자리는 placeholder 계약만 둔다.
 */
public record EasyExecutiveSummaryResponse(
    Long businessId,
    String businessName,
    LocalDate currentFrom,
    LocalDate currentTo,
    LocalDate previousFrom,
    LocalDate previousTo,
    long currentIncome,
    long currentExpense,
    long currentProfit,
    long previousIncome,
    long previousExpense,
    long previousProfit,
    long incomeDiff,
    Double incomeRate,
    long expenseDiff,
    Double expenseRate,
    long profitDiff,
    Double profitRate,
    long receivable,
    long studentCount,
    List<EasyBusinessSlice> slices,
    List<String> briefing,
    EasyIncomeTaxPreview incomeTaxPreview
) {
  public record EasyBusinessSlice(
      Long businessId,
      String businessName,
      long currentIncome,
      long currentExpense,
      long currentProfit,
      long previousIncome,
      long previousExpense,
      long previousProfit
  ) {
  }

  /**
   * 올해 장부 기준 종소세 예상 자리 — 실제 계산 없음.
   * Claude 실데이터 확보 후 설계한다. 가짜 세액을 만들지 않는다.
   */
  public record EasyIncomeTaxPreview(
      String status,
      String note
  ) {
    public static EasyIncomeTaxPreview pending() {
      return new EasyIncomeTaxPreview(
          "PENDING_REAL_DATA",
          "실제 이지샵 장부·신고 자료 확보 후 계산 로직을 설계합니다. 현재 예상세액을 표시하지 않습니다.");
    }
  }
}
