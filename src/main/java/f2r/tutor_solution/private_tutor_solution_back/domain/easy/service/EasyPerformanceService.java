package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceReport;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 장부요약/실적관리 (월별 매트릭스) — POSTED journal만 집계한다.
 *
 * <ul>
 *   <li>SOURCE OF TRUTH는 EASY POSTED journal. EasyShop은 UI/분류 참고용이며
 *       금액 대사를 하지 않는다.</li>
 *   <li>집계는 {@link EasyLedgerInquiryService#trialBalance} 재사용.
 *       새 회계 계산 엔진을 만들지 않는다.</li>
 *   <li>P/L 안전: direction이 INCOME/EXPENSE인 계정만 집계한다.
 *       ASSET/LIABILITY/EQUITY(대출·카드대금·사업주인출·계좌이체·노란우산 등)는
 *       구조상 포함될 수 없다.</li>
 *   <li>표시 그룹은 COA 실계정(V974/V978/V979) + V978·V979 Golden 매핑 기준.
 *       TB에 있으나 그룹에 없는 P/L 계정은 별도 행으로 노출하여
 *       월 합계와 연간 손익계산서가 항상 일치하도록 한다 (금액 소실 금지).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyPerformanceService {

  private final EasyLedgerInquiryService inquiryService;
  private final EasyFinancialStatementService statementService;
  private final EasyBusinessRepository businessRepository;
  private final f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository accountRepository;

  /** 계정 코드 → ID (drill-down용). 미존재 코드는 예외. */
  public Long accountIdByCode(String accountCode) {
    if (accountCode == null || accountCode.isBlank()) {
      throw new IllegalArgumentException("계정 코드를 입력해 주세요.");
    }
    return accountRepository.findByCode(accountCode.trim().toUpperCase())
        .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다: " + accountCode))
        .getAccountId();
  }

  /** 표시 그룹 — 순서대로 화면 행이 된다. */
  public record DisplayGroup(String groupKey, String displayName, String accountCode) {
  }

  /** 그룹별 월 시리즈 — monthly[0]=1월, total=연 누계. */
  public record GroupSeries(String groupKey, String displayName, String accountCode,
      List<Long> monthly, long total) {
  }

  /** 월 합계 — income/expense/profit + 누적 손익. */
  public record MonthTotal(int month, long income, long expense, long profit,
      long cumulativeProfit, boolean trialBalanced) {
  }

  /** 실적관리 응답 — 연 1회 호출로 matrix 전체를 반환한다. */
  public record PerformanceReport(String businessCode, int year,
      List<GroupSeries> incomeGroups, List<GroupSeries> expenseGroups,
      List<MonthTotal> months, long annualIncome, long annualExpense, long annualProfit,
      boolean annualTrialBalanced, boolean annualMatchesIncomeStatement) {
  }

  static final List<DisplayGroup> INCOME_GROUPS = List.of(
      new DisplayGroup("TUITION", "수강/교육매출", "INCOME_TUITION"),
      new DisplayGroup("BOOKS", "교재매출", "INCOME_BOOKS"),
      new DisplayGroup("OTHER_INCOME", "기타수입", "INCOME_OTHER"));

  static final List<DisplayGroup> EXPENSE_GROUPS = List.of(
      new DisplayGroup("SALARY", "급여/강사비", "EXPENSE_SALARY"),
      new DisplayGroup("RENT", "임차료", "EXPENSE_RENT"),
      new DisplayGroup("ADMIN", "관리비", "EXPENSE_ADMIN"),
      new DisplayGroup("PRINT", "교재비", "EXPENSE_PRINT"),
      new DisplayGroup("AD", "광고비", "EXPENSE_AD"),
      new DisplayGroup("SUPPLIES", "소모품비", "EXPENSE_SUPPLIES"),
      new DisplayGroup("FEE", "지급수수료", "EXPENSE_FEE"),
      new DisplayGroup("TAX", "세금/공과", "EXPENSE_TAX"),
      new DisplayGroup("ENTERTAIN", "식대", "EXPENSE_ENTERTAIN"),
      new DisplayGroup("INSURANCE", "보험료", "EXPENSE_INSURANCE"),
      new DisplayGroup("TRAVEL", "여비교통비", "EXPENSE_TRAVEL"),
      new DisplayGroup("COMM", "통신비", "EXPENSE_COMMUNICATION"),
      new DisplayGroup("OTHER_EXPENSE", "업무관련비(미분류)/기타비용", "EXPENSE_OTHER"));

  /** 연 실적 — businessCode(예: ACADEMY, 기본값) + year(예: 2026). */
  public PerformanceReport performance(String businessCode, int year) {
    String code = businessCode == null || businessCode.isBlank()
        ? "ACADEMY" : businessCode.trim().toUpperCase();
    EasyBusiness business = businessRepository.findAllByActiveTrueOrderBySortOrderAsc()
        .stream()
        .filter(b -> code.equalsIgnoreCase(b.getCode()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다: " + code));
    Long businessId = business.getBusinessId();
    if (year < 2000 || year > 2100) {
      throw new IllegalArgumentException("연도가 올바르지 않습니다: " + year);
    }

    // 월별 TB를 재사용한다. POSTED 분개만 포함되므로 미분개 txn은 제외된다.
    // P/L 안전장치: INCOME/EXPENSE 방향별로 분리 보관한다.
    // ASSET/LIABILITY/EQUITY 행은 여기서 버려지므로 실적에 포함될 수 없다.
    List<Map<String, Long>> monthlyIncome = new ArrayList<>();
    List<Map<String, Long>> monthlyExpense = new ArrayList<>();
    List<Boolean> monthlyBalanced = new ArrayList<>();
    for (int month = 1; month <= 12; month++) {
      LocalDate from = LocalDate.of(year, month, 1);
      LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
      TrialBalanceReport trial = inquiryService.trialBalance(businessId, from, to);
      Map<String, Long> incomeByAccount = new LinkedHashMap<>();
      Map<String, Long> expenseByAccount = new LinkedHashMap<>();
      for (TrialBalanceRow row : trial.rows()) {
        if (row.direction() == EasyAccountDirection.INCOME) {
          incomeByAccount.merge(row.accountCode(), row.endingBalance(), Long::sum);
        } else if (row.direction() == EasyAccountDirection.EXPENSE) {
          expenseByAccount.merge(row.accountCode(), row.endingBalance(), Long::sum);
        }
      }
      monthlyIncome.add(incomeByAccount);
      monthlyExpense.add(expenseByAccount);
      monthlyBalanced.add(trial.balanced());
    }

    List<GroupSeries> incomeGroups = buildSeries(INCOME_GROUPS, monthlyIncome);
    List<GroupSeries> expenseGroups = buildSeries(EXPENSE_GROUPS, monthlyExpense);
    // 그룹에 없는 P/L 계정이 있으면 별도 행으로 노출한다 (금액 소실 방지).
    appendUnmappedGroups(incomeGroups, monthlyIncome);
    appendUnmappedGroups(expenseGroups, monthlyExpense);

    List<MonthTotal> months = new ArrayList<>();
    long cumulative = 0L;
    long annualIncome = 0L;
    long annualExpense = 0L;
    boolean annualBalanced = true;
    for (int month = 1; month <= 12; month++) {
      long income = sumGroups(incomeGroups, month - 1);
      long expense = sumGroups(expenseGroups, month - 1);
      // 방어: TB 원시합과 그룹합이 다르면 집계 누락이므로 실패한다 (조용히 넘기지 않음).
      long rawIncome = monthlyIncome.get(month - 1).values().stream()
          .mapToLong(Long::longValue).sum();
      long rawExpense = monthlyExpense.get(month - 1).values().stream()
          .mapToLong(Long::longValue).sum();
      if (income != rawIncome || expense != rawExpense) {
        throw new IllegalStateException(
            "실적 집계 누락: " + year + "-" + month + " (income " + income + " vs " + rawIncome
                + ", expense " + expense + " vs " + rawExpense + ")");
      }
      long profit = income - expense;
      cumulative += profit;
      annualIncome += income;
      annualExpense += expense;
      boolean balanced = monthlyBalanced.get(month - 1);
      annualBalanced = annualBalanced && balanced;
      months.add(new MonthTotal(month, income, expense, profit, cumulative, balanced));
    }
    long annualProfit = annualIncome - annualExpense;

    // 연간 손익계산서와 내부 일관성 검증 (EasyShop 비교가 아님).
    EasyFinancialStatementService.IncomeStatement annual =
        statementService.incomeStatement(businessId,
            LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    boolean matches =
        annual.revenue() == annualIncome && annual.expense() == annualExpense;

    return new PerformanceReport(code, year, incomeGroups, expenseGroups, months,
        annualIncome, annualExpense, annualProfit, annualBalanced, matches);
  }

  private List<GroupSeries> buildSeries(List<DisplayGroup> groups,
      List<Map<String, Long>> monthlyByAccount) {
    List<GroupSeries> result = new ArrayList<>();
    for (DisplayGroup group : groups) {
      List<Long> monthly = new ArrayList<>();
      for (Map<String, Long> byAccount : monthlyByAccount) {
        monthly.add(byAccount.getOrDefault(group.accountCode(), 0L));
      }
      long total = monthly.stream().mapToLong(Long::longValue).sum();
      result.add(new GroupSeries(group.groupKey(), group.displayName(),
          group.accountCode(), monthly, total));
    }
    return result;
  }

  private void appendUnmappedGroups(List<GroupSeries> series,
      List<Map<String, Long>> monthly) {
    Map<String, Boolean> known = new LinkedHashMap<>();
    for (GroupSeries group : series) {
      known.put(group.accountCode(), Boolean.TRUE);
    }
    List<String> extra = new ArrayList<>();
    for (Map<String, Long> byAccount : monthly) {
      for (String accountCode : byAccount.keySet()) {
        if (!known.containsKey(accountCode) && !extra.contains(accountCode)) {
          extra.add(accountCode);
        }
      }
    }
    for (String accountCode : extra) {
      List<Long> amounts = new ArrayList<>();
      for (Map<String, Long> byAccount : monthly) {
        amounts.add(byAccount.getOrDefault(accountCode, 0L));
      }
      long total = amounts.stream().mapToLong(Long::longValue).sum();
      if (total != 0L) {
        series.add(new GroupSeries("EXTRA_" + accountCode, accountCode + "(기타)",
            accountCode, amounts, total));
      }
    }
  }

  private long sumGroups(List<GroupSeries> series, int monthIndex) {
    return series.stream().mapToLong(g -> g.monthly().get(monthIndex)).sum();
  }
}
