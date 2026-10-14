package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceReport;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 사업소득 계산 (Phase Tax-1) — ACCOUNTING LEDGER → TAX ADJUSTMENT →
 * BUSINESS TAXABLE INCOME.
 *
 * 회계 장부를 세금 결과에 맞춰 수정하지 않는다. 세법상 불인정분은 조정으로만
 * 반영하고 원장 transaction/journal은 untouched이다 (테스트로 보장).
 * 세무 판단 상태: DEDUCTIBLE / NON_DEDUCTIBLE / REVIEW_REQUIRED / NOT_APPLICABLE.
 * 판단 근거·출처를 조정마다 남긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyBusinessTaxComputation {

  private final EasyLedgerInquiryService inquiryService;

  /** 세무 판단 상태. */
  public enum ReviewState {
    DEDUCTIBLE,
    NON_DEDUCTIBLE,
    REVIEW_REQUIRED,
    NOT_APPLICABLE
  }

  /** 세무조정 — 장부를 바꾸지 않고 과세소득만 조정한다. */
  public record TaxAdjustment(Long txnId, Long entryId, long amount,
      ReviewState state, String reason, String source) {
  }

  /** 조정 종류 — 수입 가산 / 비용 불인정. */
  public enum AdjustmentKind {
    ADD_TO_INCOME,
    REMOVE_FROM_EXPENSE
  }

  public record TypedAdjustment(AdjustmentKind kind, TaxAdjustment adjustment) {
  }

  /** 사업장 사업소득 — 원장 수치 + 조정 + 상태. */
  public record BusinessTaxIncome(Long businessId, String businessCode, int fiscalYear,
      long bookRevenue, long bookExpense, long deductibleExpense, long businessIncome,
      List<TypedAdjustment> adjustments, ReviewState overallState, List<String> missing) {
  }

  /**
   * 사업장 사업소득 계산 — journal TB 기반.
   * 장부 자료가 없으면 MISSING_INPUT相当 REVIEW_REQUIRED (추측 계산 금지).
   */
  public BusinessTaxIncome compute(Long businessId, String businessCode, int fiscalYear,
      List<TypedAdjustment> adjustments) {
    LocalDate from = LocalDate.of(fiscalYear, 1, 1);
    LocalDate to = LocalDate.of(fiscalYear, 12, 31);
    TrialBalanceReport trial = inquiryService.trialBalance(businessId, from, to);
    long revenue = 0L;
    long expense = 0L;
    for (TrialBalanceRow row : trial.rows()) {
      switch (row.direction()) {
        case INCOME -> revenue += row.endingBalance();
        case EXPENSE -> expense += row.endingBalance();
        default -> {
        }
      }
    }
    List<String> missing = new ArrayList<>();
    if (trial.rows().isEmpty()) {
      missing.add("MISSING_INPUT: 해당 귀속연도 분개 없음");
    }
    long nonDeductible = 0L;
    long addToIncome = 0L;
    ReviewState overall = ReviewState.DEDUCTIBLE;
    List<TypedAdjustment> applied = adjustments == null ? List.of() : adjustments;
    for (TypedAdjustment typed : applied) {
      TaxAdjustment adj = typed.adjustment();
      if (adj.state() == ReviewState.REVIEW_REQUIRED) {
        overall = ReviewState.REVIEW_REQUIRED;
        missing.add("REVIEW_REQUIRED: " + adj.reason());
      } else if (adj.state() == ReviewState.NON_DEDUCTIBLE
          && typed.kind() == AdjustmentKind.REMOVE_FROM_EXPENSE) {
        nonDeductible += adj.amount();
      } else if (typed.kind() == AdjustmentKind.ADD_TO_INCOME
          && adj.state() == ReviewState.DEDUCTIBLE) {
        addToIncome += adj.amount();
      }
    }
    if (!missing.isEmpty() && overall != ReviewState.REVIEW_REQUIRED) {
      overall = ReviewState.REVIEW_REQUIRED;
    }
    long deductibleExpense = expense - nonDeductible;
    long businessIncome = (revenue + addToIncome) - deductibleExpense;
    return new BusinessTaxIncome(businessId, businessCode, fiscalYear, revenue, expense,
        deductibleExpense, businessIncome, applied, overall, missing);
  }
}
