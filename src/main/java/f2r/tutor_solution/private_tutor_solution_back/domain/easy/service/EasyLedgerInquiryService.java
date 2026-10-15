package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 회계 조회 (Phase A2) — 총계정원장·시산표·분개 균형 감사.
 *
 * 모든 수치는 journal_entries + journal_lines에서 계산한다.
 * 별도 수기 GL 숫자를 저장하지 않는다 (journal이 SOURCE OF TRUTH).
 * Historical SETTLEMENT 분개도 동일 기준으로 집계·감사한다 (사용 보존, 균형 예외 없음).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyLedgerInquiryService {

  private final EasyJournalEntryRepository entryRepository;
  private final EasyJournalLineRepository lineRepository;
  private final EasyAccountRepository accountRepository;

  /** 원장 행 — runningBalance는 계정 정상잔액 부호로 표시한다. */
  public record LedgerRow(LocalDate date, Long journalEntryId, String sourceRef,
      String memo, long debit, long credit, long runningBalance) {
  }

  /** 계정 원장 — opening은 조회 시작일 이전 합산, closing은 기말 잔액. */
  public record AccountLedger(EasyAccount account, long openingBalance,
      List<LedgerRow> rows, long periodDebit, long periodCredit, long closingBalance) {
  }

  /** 시산표 계정행 — endingBalance는 정상잔액 부호. */
  public record TrialBalanceRow(String accountCode, String accountName,
      EasyAccountDirection direction, long totalDebit, long totalCredit, long endingBalance) {
  }

  /** 시산표 — 불균형이면 숨기지 않고 mismatchedEntryIds를 제공한다. */
  public record TrialBalanceReport(List<TrialBalanceRow> rows, long totalDebit,
      long totalCredit, long difference, boolean balanced, List<Long> mismatchedEntryIds) {
  }

  /** 분개 균형 감사 — 운영 데이터를 수정하지 않는 read-only 검사. */
  public record BalanceAudit(int totalEntries, int balancedEntries,
      List<Long> unbalancedEntryIds) {
  }

  /**
   * 총계정원장 — 지정 계정의 기간 내 분개 + running 잔액.
   * ASSET/EXPENSE는 차변 정상, LIABILITY/EQUITY/INCOME은 대변 정상.
   */
  public AccountLedger generalLedger(Long businessId, Long accountId,
      LocalDate from, LocalDate to) {
    EasyAccount account = accountRepository.findById(accountId)
        .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
    LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
    LocalDate end = to == null ? LocalDate.of(2100, 12, 31) : to;

    List<EntryLeg> legs = postedLegs(businessId, LocalDate.of(2000, 1, 1),
        LocalDate.of(2100, 12, 31));
    long openingNet = 0L;
    List<LedgerRow> rows = new ArrayList<>();
    long periodDebit = 0L;
    long periodCredit = 0L;
    for (EntryLeg leg : legs) {
      if (!leg.accountId().equals(accountId)) {
        continue;
      }
      long net = leg.debit() - leg.credit();
      if (leg.date().isBefore(start)) {
        openingNet += net;
      } else if (!leg.date().isAfter(end)) {
        periodDebit += leg.debit();
        periodCredit += leg.credit();
        rows.add(new LedgerRow(leg.date(), leg.entryId(), leg.sourceRef(), leg.memo(),
            leg.debit(), leg.credit(), 0L));
      }
    }
    boolean creditNormal = isCreditNormal(account.getDirection());
    long running = signed(openingNet, creditNormal);
    List<LedgerRow> withBalance = new ArrayList<>();
    for (LedgerRow row : rows) {
      running += creditNormal ? row.credit() - row.debit() : row.debit() - row.credit();
      withBalance.add(new LedgerRow(row.date(), row.journalEntryId(), row.sourceRef(),
          row.memo(), row.debit(), row.credit(), running));
    }
    long closingNet = openingNet + periodDebit - periodCredit;
    return new AccountLedger(account, signed(openingNet, creditNormal), withBalance,
        periodDebit, periodCredit, signed(closingNet, creditNormal));
  }

  /** 시산표 — 범위 내 활성 분개의 계정별 합산 + 전체 검증. */
  public TrialBalanceReport trialBalance(Long businessId, LocalDate from, LocalDate to) {
    LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
    LocalDate end = to == null ? LocalDate.of(2100, 12, 31) : to;
    Map<Long, Accumulator> byAccount = new LinkedHashMap<>();
    long totalDebit = 0L;
    long totalCredit = 0L;
    List<Long> mismatched = new ArrayList<>();
    for (EasyJournalEntry entry : postedEntries(businessId, start, end)) {
      List<EasyJournalLine> lines =
          lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(entry.getEntryId());
      long debit = 0L;
      long credit = 0L;
      for (EasyJournalLine line : lines) {
        debit += line.getDebit();
        credit += line.getCredit();
        byAccount.computeIfAbsent(line.getAccount().getAccountId(),
            id -> new Accumulator(line.getAccount())).add(line.getDebit(), line.getCredit());
      }
      totalDebit += debit;
      totalCredit += credit;
      if (debit != credit) {
        mismatched.add(entry.getEntryId());
      }
    }
    List<TrialBalanceRow> rows = new ArrayList<>();
    for (Accumulator acc : byAccount.values()) {
      boolean creditNormal = isCreditNormal(acc.account.getDirection());
      long net = acc.debit - acc.credit;
      rows.add(new TrialBalanceRow(acc.account.getCode(), acc.account.getName(),
          acc.account.getDirection(), acc.debit, acc.credit, signed(net, creditNormal)));
    }
    rows.sort(Comparator.comparing(TrialBalanceRow::accountCode));
    long difference = totalDebit - totalCredit;
    return new TrialBalanceReport(rows, totalDebit, totalCredit, difference,
        difference == 0 && mismatched.isEmpty(), mismatched);
  }

  /** 날짜별 분개장 — 사용자가 거래와 차변·대변을 함께 확인하는 read-only 장부. */
  public record JournalLineRow(String accountCode, String accountName,
      long debit, long credit) {
  }

  public record JournalEntryRow(Long journalEntryId, Long transactionId,
      LocalDate date, String counterparty, String description, String memo,
      List<JournalLineRow> lines, long totalDebit, long totalCredit, boolean balanced) {
  }

  public List<JournalEntryRow> journal(Long businessId, LocalDate from, LocalDate to) {
    LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
    LocalDate end = to == null ? LocalDate.of(2100, 12, 31) : to;
    List<JournalEntryRow> result = new ArrayList<>();
    for (EasyJournalEntry entry : postedEntries(businessId, start, end)) {
      List<JournalLineRow> lines = new ArrayList<>();
      long debit = 0L;
      long credit = 0L;
      for (EasyJournalLine line : lineRepository
          .findByEntry_EntryIdOrderBySortOrderAsc(entry.getEntryId())) {
        debit += line.getDebit();
        credit += line.getCredit();
        lines.add(new JournalLineRow(line.getAccount().getCode(),
            line.getAccount().getName(), line.getDebit(), line.getCredit()));
      }
      var txn = entry.getTxn();
      result.add(new JournalEntryRow(entry.getEntryId(),
          txn == null ? null : txn.getTxnId(), entry.getOccurredOn(),
          txn == null ? null : txn.getCounterparty(),
          txn == null ? null : txn.getDescription(), entry.getMemo(),
          lines, debit, credit, debit == credit));
    }
    return result;
  }

  /** 분개 균형 감사 — entry별 ΣDr/ΣCr 비교, 수정 없음. */
  public BalanceAudit auditBalance(Long businessId, LocalDate from, LocalDate to) {
    LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
    LocalDate end = to == null ? LocalDate.of(2100, 12, 31) : to;
    List<EasyJournalEntry> entries = postedEntries(businessId, start, end);
    List<Long> unbalanced = new ArrayList<>();
    for (EasyJournalEntry entry : entries) {
      long debit = 0L;
      long credit = 0L;
      for (EasyJournalLine line : lineRepository
          .findByEntry_EntryIdOrderBySortOrderAsc(entry.getEntryId())) {
        debit += line.getDebit();
        credit += line.getCredit();
      }
      if (debit != credit) {
        unbalanced.add(entry.getEntryId());
      }
    }
    return new BalanceAudit(entries.size(), entries.size() - unbalanced.size(), unbalanced);
  }

  private List<EasyJournalEntry> postedEntries(Long businessId, LocalDate start,
      LocalDate end) {
    if (businessId == null) {
      return entryRepository.findByOccurredOnBetweenAndStatusOrderByEntryIdAsc(
          start, end, EasyJournalStatus.POSTED);
    }
    return entryRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
            businessId, start, end, EasyJournalStatus.POSTED);
  }

  private List<EntryLeg> postedLegs(Long businessId, LocalDate start, LocalDate end) {
    List<EntryLeg> legs = new ArrayList<>();
    for (EasyJournalEntry entry : postedEntries(businessId, start, end)) {
      for (EasyJournalLine line : lineRepository
          .findByEntry_EntryIdOrderBySortOrderAsc(entry.getEntryId())) {
        String sourceRef = entry.getTxn() == null ? null : entry.getTxn().getSourceRef();
        legs.add(new EntryLeg(entry.getOccurredOn(), entry.getEntryId(),
            line.getAccount().getAccountId(), line.getDebit(), line.getCredit(),
            sourceRef, entry.getMemo()));
      }
    }
    legs.sort(Comparator.comparing(EntryLeg::date).thenComparing(EntryLeg::entryId));
    return legs;
  }

  private record EntryLeg(LocalDate date, Long entryId, Long accountId, long debit,
      long credit, String sourceRef, String memo) {
  }

  private static class Accumulator {
    final EasyAccount account;
    long debit;
    long credit;

    Accumulator(EasyAccount account) {
      this.account = account;
    }

    void add(long debit, long credit) {
      this.debit += debit;
      this.credit += credit;
    }
  }

  static boolean isCreditNormal(EasyAccountDirection direction) {
    return direction == EasyAccountDirection.LIABILITY
        || direction == EasyAccountDirection.EQUITY
        || direction == EasyAccountDirection.INCOME;
  }

  /** 차변순액(net=Dr-Cr)을 계정 정상잔액 부호로 변환한다. */
  static long signed(long net, boolean creditNormal) {
    return creditNormal ? -net : net;
  }
}
