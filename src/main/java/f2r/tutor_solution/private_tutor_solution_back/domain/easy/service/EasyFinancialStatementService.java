package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceReport;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 재무제표 (Phase A3) — Journal → GL → TB에서만 계산한다.
 *
 * 기존 {@code GET /api/admin/easy/pl}(EasyReportService, txn 기반 장부 집계)과는
 * 다른 source이며, 이번 Phase에서 대규모 rewrite하지 않는다. 차이는 다음과 같다:
 * - legacy /pl: easy_transactions 직접 집계 (SETTLEMENT 제외, FUNDS 제외, 역분개 네팅)
 * - 본 서비스: journal_lines 합산 (TB 기반, 분개된 것만 반영, 미분개 txn 제외)
 * 두 수치가 다를 수 있으며, 이는 분개 누락·미분류를 추적하는 신호로 사용한다.
 *
 * 출판사는 정식 B/S를 만들지 않는다 — 수입·비용·사업소득 자료만 제공한다.
 * 세법 판단이 필요한 부분은 계산하지 않는다 (TAX_RULE_PENDING相当 — 본 서비스는 세율·공제를 모른다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyFinancialStatementService {

  private final EasyLedgerInquiryService inquiryService;

  /** P&L 계정행. */
  public record ProfitLine(String accountCode, String accountName, long amount) {
  }

  /** 손익계산서 — NET INCOME = REVENUE - EXPENSE (TB 기반). */
  public record IncomeStatement(List<ProfitLine> revenueLines, List<ProfitLine> expenseLines,
      long revenue, long expense, long netIncome, boolean trialBalanced) {
  }

  /** B/S 계정행. */
  public record BalanceLine(String accountCode, String accountName, long balance) {
  }

  /**
   * 재무상태표 — 기준일 누적 TB에서 ASSET/LIABILITY/EQUITY를 집계한다.
   * 당기손익 대체분개(결산)가 없으므로 unclosedNetIncome을 별도 표시하고,
   * strict equation과 unclosed 포함 equation을 모두 보고한다. 강제 맞춤 없음.
   */
  public record BalanceSheet(List<BalanceLine> assetLines, List<BalanceLine> liabilityLines,
      List<BalanceLine> equityLines, long assets, long liabilities, long equity,
      long unclosedNetIncome, long difference, boolean balanced,
      boolean balancedIncludingUnclosed) {
  }

  /** 학원 P&L — journal source. */
  public IncomeStatement incomeStatement(Long businessId, LocalDate from, LocalDate to) {
    TrialBalanceReport trial = inquiryService.trialBalance(businessId, from, to);
    List<ProfitLine> revenues = new ArrayList<>();
    List<ProfitLine> expenses = new ArrayList<>();
    long revenue = 0L;
    long expense = 0L;
    for (TrialBalanceRow row : trial.rows()) {
      if (row.direction() == EasyAccountDirection.INCOME) {
        revenues.add(new ProfitLine(row.accountCode(), row.accountName(), row.endingBalance()));
        revenue += row.endingBalance();
      } else if (row.direction() == EasyAccountDirection.EXPENSE) {
        expenses.add(new ProfitLine(row.accountCode(), row.accountName(), row.endingBalance()));
        expense += row.endingBalance();
      }
    }
    return new IncomeStatement(revenues, expenses, revenue, expense,
        revenue - expense, trial.balanced());
  }

  /** 학원 B/S — 기준일 누적. */
  public BalanceSheet balanceSheet(Long businessId, LocalDate atDate) {
    LocalDate end = atDate == null ? LocalDate.of(2100, 12, 31) : atDate;
    TrialBalanceReport trial = inquiryService.trialBalance(businessId, null, end);
    List<BalanceLine> assets = new ArrayList<>();
    List<BalanceLine> liabilities = new ArrayList<>();
    List<BalanceLine> equities = new ArrayList<>();
    long assetTotal = 0L;
    long liabilityTotal = 0L;
    long equityTotal = 0L;
    long revenue = 0L;
    long expense = 0L;
    for (TrialBalanceRow row : trial.rows()) {
      switch (row.direction()) {
        case ASSET -> {
          assets.add(new BalanceLine(row.accountCode(), row.accountName(), row.endingBalance()));
          assetTotal += row.endingBalance();
        }
        case LIABILITY -> {
          liabilities.add(
              new BalanceLine(row.accountCode(), row.accountName(), row.endingBalance()));
          liabilityTotal += row.endingBalance();
        }
        case EQUITY -> {
          equities.add(
              new BalanceLine(row.accountCode(), row.accountName(), row.endingBalance()));
          equityTotal += row.endingBalance();
        }
        case INCOME -> revenue += row.endingBalance();
        case EXPENSE -> expense += row.endingBalance();
      }
    }
    long unclosed = revenue - expense;
    long difference = assetTotal - (liabilityTotal + equityTotal);
    long differenceIncludingUnclosed =
        assetTotal - (liabilityTotal + equityTotal + unclosed);
    return new BalanceSheet(assets, liabilities, equities, assetTotal, liabilityTotal,
        equityTotal, unclosed, difference, difference == 0,
        differenceIncludingUnclosed == 0);
  }

  /** 출판사 사업소득 자료 — 수입·비용·사업소득 (B/S 없음). */
  public IncomeStatement businessIncome(Long businessId, LocalDate from, LocalDate to) {
    return incomeStatement(businessId, from, to);
  }
}
