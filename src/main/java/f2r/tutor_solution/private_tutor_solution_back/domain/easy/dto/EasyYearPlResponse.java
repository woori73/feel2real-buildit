package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/**
 * Easy 연도 손익 — 분개(journal) 기준 집계 + 장부(ledger) 교차검증.
 * 분개와 장부가 다르면 matchesLedger=false (같은 경제거래 이중 계상 아님 — 누락 표시).
 */
public record EasyYearPlResponse(
    Long businessId,
    String businessName,
    int year,
    long income,
    long expense,
    long profit,
    long journalIncome,
    long journalExpense,
    long journalProfit,
    boolean journalBalanced,
    boolean matchesLedger,
    long unpostedTxnCount,
    List<EasyMonthSlice> monthly,
    List<EasyAccountSlice> byAccount,
    EasyPrevYear prevYear
) {
  public record EasyMonthSlice(int month, long income, long expense) {
  }

  public record EasyAccountSlice(Long accountId, String accountName, String direction, long total) {
  }

  public record EasyPrevYear(int year, long income, long expense, long profit,
      Double incomeRate, Double expenseRate, Double profitRate) {
  }
}
