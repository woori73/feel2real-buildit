package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFilingCheckResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFilingCheckResponse.EasyFilingLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFilingReferenceRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFilingReferenceResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyIncomeTaxResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyIncomeTaxResponse.EasyIncomeTaxSlice;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyYearPlResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyYearPlResponse.EasyAccountSlice;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyYearPlResponse.EasyMonthSlice;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyYearPlResponse.EasyPrevYear;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyFilingReference;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyFilingReferenceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyJournalService.TrialBalance;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 결과 집계 — 연도 손익(분개 기준 + 장부 교차검증), 신고자료 대조, 종소세 합산.
 * 계산식을 역산하거나 장부를 조작하지 않는다. 차이는 그대로 보고한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyReportService {

  private final EasyTransactionRepository txnRepository;
  private final EasyBusinessRepository businessRepository;
  private final EasyJournalEntryRepository entryRepository;
  private final EasyJournalLineRepository lineRepository;
  private final EasyFilingReferenceRepository filingRepository;
  private final EasyJournalService journalService;

  // ================= 연도 손익 =================

  /** 연도 손익 — businessId null이면 전체 합산. P&L은 분개 기준, 장부와 교차검증한다. */
  public EasyYearPlResponse yearPl(Long businessId, int fiscalYear) {
    if (fiscalYear < 2000 || fiscalYear > 2100) {
      throw new IllegalArgumentException("귀속연도가 올바르지 않습니다.");
    }
    String businessName = businessId == null ? "전체"
        : businessRepository.findById(businessId).map(EasyBusiness::getName).orElse("전체");
    LocalDate from = LocalDate.of(fiscalYear, 1, 1);
    LocalDate to = LocalDate.of(fiscalYear, 12, 31);

    LedgerTotals ledger = ledgerTotals(businessId, from, to);
    JournalTotals journal = journalTotals(businessId, from, to);
    TrialBalance trial = journalService.trialBalance(businessId, from, to);

    List<EasyMonthSlice> monthly = new ArrayList<>();
    for (int month = 1; month <= 12; month++) {
      LocalDate start = LocalDate.of(fiscalYear, month, 1);
      LedgerTotals slice = ledgerTotals(businessId, start, start.withDayOfMonth(
          start.lengthOfMonth()));
      monthly.add(new EasyMonthSlice(month, slice.income(), slice.expense()));
    }

    Map<Long, AccountAccumulator> byAccount = new LinkedHashMap<>();
    for (EasyJournalEntry entry : postedEntries(businessId, from, to)) {
      for (EasyJournalLine line : lineRepository
          .findByEntry_EntryIdOrderBySortOrderAsc(entry.getEntryId())) {
        if (line.getAccount() == null
            || EasyJournalService.SETTLEMENT_CODE.equals(line.getAccount().getCode())) {
          continue;
        }
        AccountAccumulator acc = byAccount.computeIfAbsent(
            line.getAccount().getAccountId(),
            key -> new AccountAccumulator(line.getAccount().getAccountId(),
                line.getAccount().getName(),
                line.getAccount().getDirection().name()));
        if (line.getDebit() > 0) {
          acc.debit += line.getDebit();
        } else {
          acc.credit += line.getCredit();
        }
      }
    }
    List<EasyAccountSlice> accounts = byAccount.values().stream()
        .map(acc -> new EasyAccountSlice(acc.id, acc.name, acc.direction,
            "INCOME".equals(acc.direction) ? acc.credit : acc.debit))
        .sorted((left, right) -> Long.compare(right.total(), left.total()))
        .toList();

    LedgerTotals prev = ledgerTotals(businessId,
        LocalDate.of(fiscalYear - 1, 1, 1), LocalDate.of(fiscalYear - 1, 12, 31));

    return new EasyYearPlResponse(
        businessId, businessName, fiscalYear,
        ledger.income(), ledger.expense(), ledger.income() - ledger.expense(),
        journal.income(), journal.expense(), journal.income() - journal.expense(),
        trial.balanced(),
        ledger.income() == journal.income() && ledger.expense() == journal.expense(),
        unpostedCount(businessId, from, to),
        monthly, accounts,
        new EasyPrevYear(fiscalYear - 1, prev.income(), prev.expense(),
            prev.income() - prev.expense(),
            rate(ledger.income(), prev.income()),
            rate(ledger.expense(), prev.expense()),
            rate(ledger.income() - ledger.expense(), prev.income() - prev.expense())));
  }

  private record LedgerTotals(long income, long expense) {
  }

  private record JournalTotals(long income, long expense) {
  }

  private static class AccountAccumulator {
    final Long id;
    final String name;
    final String direction;
    long debit;
    long credit;

    AccountAccumulator(Long id, String name, String direction) {
      this.id = id;
      this.name = name;
      this.direction = direction;
    }
  }

  private List<EasyTransaction> scopedTxns(Long businessId, LocalDate from, LocalDate to) {
    if (businessId == null) {
      return txnRepository.findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(from, to);
    }
    return txnRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            businessId, from, to);
  }

  private LedgerTotals ledgerTotals(Long businessId, LocalDate from, LocalDate to) {
    long income = 0L;
    long expense = 0L;
    for (EasyTransaction txn : scopedTxns(businessId, from, to)) {
      if (txn.getDirection() == EasyTxnDirection.INCOME) {
        income += txn.getAmount();
      } else {
        expense += txn.getAmount();
      }
    }
    return new LedgerTotals(income, expense);
  }

  private List<EasyJournalEntry> postedEntries(Long businessId, LocalDate from, LocalDate to) {
    if (businessId == null) {
      return entryRepository.findByOccurredOnBetweenAndStatusOrderByEntryIdAsc(
          from, to, EasyJournalStatus.POSTED);
    }
    return entryRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
            businessId, from, to, EasyJournalStatus.POSTED);
  }

  private JournalTotals journalTotals(Long businessId, LocalDate from, LocalDate to) {
    long income = 0L;
    long expense = 0L;
    for (EasyJournalEntry entry : postedEntries(businessId, from, to)) {
      for (EasyJournalLine line : lineRepository
          .findByEntry_EntryIdOrderBySortOrderAsc(entry.getEntryId())) {
        if (line.getAccount() == null
            || EasyJournalService.SETTLEMENT_CODE.equals(line.getAccount().getCode())) {
          continue;
        }
        if (line.getAccount().getDirection() == EasyAccountDirection.INCOME) {
          income += line.getCredit();
        } else if (line.getAccount().getDirection() == EasyAccountDirection.EXPENSE) {
          expense += line.getDebit();
        }
      }
    }
    return new JournalTotals(income, expense);
  }

  private long unpostedCount(Long businessId, LocalDate from, LocalDate to) {
    long count = 0L;
    for (EasyTransaction txn : scopedTxns(businessId, from, to)) {
      if (txn.getAmount() <= 0 || txn.getAccount() == null) {
        continue;
      }
      boolean posted = !entryRepository
          .findByTxn_TxnIdAndStatusOrderByEntryIdDesc(txn.getTxnId(), EasyJournalStatus.POSTED)
          .isEmpty();
      if (!posted) {
        count++;
      }
    }
    return count;
  }

  private Double rate(long current, long previous) {
    if (previous == 0) {
      return null;
    }
    return (current - previous) * 100.0 / Math.abs((double) previous);
  }

  // ================= 신고 기준자료 =================

  public List<EasyFilingReferenceResponse> listReferences(Long businessId, Integer fiscalYear) {
    List<EasyFilingReference> refs;
    if (businessId != null && fiscalYear != null) {
      refs = filingRepository.findByBusiness_BusinessIdAndFiscalYear(businessId, fiscalYear)
          .map(List::of).orElseGet(List::of);
    } else if (businessId != null) {
      refs = filingRepository.findByBusiness_BusinessIdOrderByFiscalYearDesc(businessId);
    } else if (fiscalYear != null) {
      refs = filingRepository.findByFiscalYearOrderByFiscalYearDesc(fiscalYear);
    } else {
      refs = filingRepository.findAll();
    }
    return refs.stream().map(EasyFilingReferenceResponse::from).toList();
  }

  @Transactional
  public EasyFilingReferenceResponse upsertReference(EasyFilingReferenceRequest request) {
    if (request == null || request.businessId() == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (request.fiscalYear() == null || request.fiscalYear() < 2000
        || request.fiscalYear() > 2100) {
      throw new IllegalArgumentException("귀속연도가 올바르지 않습니다.");
    }
    EasyBusiness business = businessRepository.findById(request.businessId())
        .orElseThrow(() -> new IllegalArgumentException(
            "사업체를 찾을 수 없습니다: " + request.businessId()));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체에는 등록할 수 없습니다.");
    }
    EasyFilingReference ref = filingRepository
        .findByBusiness_BusinessIdAndFiscalYear(request.businessId(), request.fiscalYear())
        .orElseGet(() -> filingRepository.save(new EasyFilingReference(
            business, request.fiscalYear(), request.revenue(), request.expense(),
            request.incomeAmount(), request.source(), request.note())));
    ref.update(request.revenue(), request.expense(), request.incomeAmount(),
        request.source(), request.note());
    return EasyFilingReferenceResponse.from(ref);
  }

  @Transactional
  public void deleteReference(Long refId) {
    EasyFilingReference ref = filingRepository.findById(refId)
        .orElseThrow(() -> new IllegalArgumentException(
            "신고 기준자료를 찾을 수 없습니다: " + refId));
    filingRepository.delete(ref);
  }

  // ================= 신고자료 대조 =================

  /** 장부 vs 신고자료 독립 비교 — 역산 금지, 설명 불가 차이는 NEEDS_REVIEW/UNKNOWN. */
  public EasyFilingCheckResponse filingCheck(Long businessId, int fiscalYear) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요. (사업장별 대조만 지원)");
    }
    if (fiscalYear < 2000 || fiscalYear > 2100) {
      throw new IllegalArgumentException("귀속연도가 올바르지 않습니다.");
    }
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException(
            "사업체를 찾을 수 없습니다: " + businessId));
    LedgerTotals ledger = ledgerTotals(businessId,
        LocalDate.of(fiscalYear, 1, 1), LocalDate.of(fiscalYear, 12, 31));
    EasyFilingReference ref = filingRepository
        .findByBusiness_BusinessIdAndFiscalYear(businessId, fiscalYear).orElse(null);

    List<EasyFilingLine> lines = new ArrayList<>();
    lines.add(compare("수입금액", ledger.income(),
        ref == null ? null : ref.getRevenue()));
    lines.add(compare("필요경비", ledger.expense(),
        ref == null ? null : ref.getExpense()));
    lines.add(compare("소득금액(장부손익 vs 신고소득)", ledger.income() - ledger.expense(),
        ref == null ? null : ref.getIncomeAmount()));

    return new EasyFilingCheckResponse(
        businessId, business.getName(), fiscalYear,
        ledger.income(), ledger.expense(), ledger.income() - ledger.expense(),
        ref == null ? null : ref.getRevenue(),
        ref == null ? null : ref.getExpense(),
        ref == null ? null : ref.getIncomeAmount(),
        ref == null ? null : ref.getSource(),
        lines,
        ref == null
            ? "기준자료가 없습니다. Library 신고자료를 등록하면 대조합니다."
            : "차이는 역산하지 않습니다. 세무조정 자료가 없어 설명할 수 없는 차이는 NEEDS_REVIEW로 둡니다.");
  }

  private EasyFilingLine compare(String label, long ledger, Long reference) {
    if (reference == null) {
      return new EasyFilingLine(label, ledger, null, null, "UNKNOWN");
    }
    long diff = ledger - reference;
    return new EasyFilingLine(label, ledger, reference, diff,
        diff == 0 ? "MATCHED" : "NEEDS_REVIEW");
  }

  // ================= 종합소득세 준비 =================

  /**
   * 개인 귀속연도 합산 — 장부 DB는 합치지 않고 이 조회에서만 합산한다.
   * 학원(ACADEMY 코드)을 주사업장으로 표시한다. 결정세액은 표시하지 않는다.
   */
  public EasyIncomeTaxResponse incomeTax(int year) {
    if (year < 2000 || year > 2100) {
      throw new IllegalArgumentException("귀속연도가 올바르지 않습니다.");
    }
    List<EasyBusiness> businesses = businessRepository.findAll().stream()
        .filter(EasyBusiness::isActive)
        .sorted((left, right) -> Integer.compare(left.getSortOrder(), right.getSortOrder()))
        .toList();
    List<EasyIncomeTaxSlice> slices = new ArrayList<>();
    long combinedRevenue = 0L;
    long combinedExpense = 0L;
    for (EasyBusiness business : businesses) {
      LedgerTotals ledger = ledgerTotals(business.getBusinessId(),
          LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
      EasyFilingReference ref = filingRepository
          .findByBusiness_BusinessIdAndFiscalYear(business.getBusinessId(), year).orElse(null);
      String cause;
      if (ref == null || ref.getRevenue() == null) {
        cause = "UNKNOWN";
      } else {
        cause = ledger.income() == ref.getRevenue() ? "MATCHED" : "NEEDS_REVIEW";
      }
      slices.add(new EasyIncomeTaxSlice(
          business.getBusinessId(), business.getName(),
          "ACADEMY".equalsIgnoreCase(business.getCode()),
          ledger.income(), ledger.expense(), ledger.income() - ledger.expense(),
          ref == null ? null : ref.getRevenue(), cause));
      combinedRevenue += ledger.income();
      combinedExpense += ledger.expense();
    }
    return new EasyIncomeTaxResponse(
        year, slices, combinedRevenue, combinedExpense, combinedRevenue - combinedExpense,
        "PREPARING",
        "사업소득 합산까지만 표시합니다. 세무조정·소득공제·세액공제·기납부세액 데이터가 없어 "
            + "결정세액을 확정값으로 표시하지 않습니다.");
  }
}
