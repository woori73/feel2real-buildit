package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAnnualClosing;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCutover;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCutoverStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTaxClassification;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCutoverRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.AccountLedger;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.BalanceAudit;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceReport;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyOpeningService.OpeningLeg;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Easy Phase A2 원장·시산표·마감·개시 테스트")
class EasyPhaseA2Test {

  @Mock EasyJournalEntryRepository entryRepository;
  @Mock EasyJournalLineRepository lineRepository;
  @Mock EasyAccountRepository accountRepository;
  @Mock EasyAnnualClosingRepository closingRepository;
  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyCutoverRepository cutoverRepository;

  private EasyJournalService journalService;
  private EasyLedgerInquiryService inquiryService;
  private EasyRealPostingService postingService;
  private EasyOpeningService openingService;
  private EasyBusiness academy;

  private EasyAccount supplies;
  private EasyAccount cardPay;
  private EasyAccount bankShinhan;
  private EasyAccount bankKb;
  private EasyAccount loan;
  private EasyAccount settlement;

  private final Map<Long, EasyJournalEntry> entries = new LinkedHashMap<>();
  private final Map<Long, List<EasyJournalLine>> linesByEntry = new LinkedHashMap<>();
  private final AtomicLong entryIds = new AtomicLong(1L);

  @BeforeEach
  void setUp() {
    journalService = new EasyJournalService(entryRepository, lineRepository,
        accountRepository, closingRepository);
    inquiryService = new EasyLedgerInquiryService(entryRepository, lineRepository,
        accountRepository);
    postingService = new EasyRealPostingService(journalService, txnRepository,
        accountRepository, businessRepository);
    openingService = new EasyOpeningService(journalService, businessRepository,
        accountRepository, cutoverRepository);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(academy, "businessId", 1L);
    supplies = account(11L, "EXPENSE_SUPPLIES", "소모품", EasyAccountDirection.EXPENSE);
    cardPay = account(12L, "CARD_PAY_SHINHAN", "미지급금-신한카드",
        EasyAccountDirection.LIABILITY);
    bankShinhan = account(13L, "BANK_SHINHAN", "보통예금-신한", EasyAccountDirection.ASSET);
    bankKb = account(14L, "BANK_KB", "보통예금-KB", EasyAccountDirection.ASSET);
    loan = account(15L, "LOAN_PAYABLE", "차입금", EasyAccountDirection.LIABILITY);
    settlement = account(17L, "SETTLEMENT", "자금정산", EasyAccountDirection.ASSET);

    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(accountRepository.findById(11L)).thenReturn(Optional.of(supplies));
    when(accountRepository.findById(12L)).thenReturn(Optional.of(cardPay));
    when(accountRepository.findById(13L)).thenReturn(Optional.of(bankShinhan));
    when(accountRepository.findById(14L)).thenReturn(Optional.of(bankKb));
    when(accountRepository.findById(15L)).thenReturn(Optional.of(loan));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(entryRepository.save(any(EasyJournalEntry.class))).thenAnswer(invocation -> {
      EasyJournalEntry entry = invocation.getArgument(0);
      ReflectionTestUtils.setField(entry, "entryId", entryIds.getAndIncrement());
      entries.put(entry.getEntryId(), entry);
      linesByEntry.put(entry.getEntryId(), new ArrayList<>());
      return entry;
    });
    when(lineRepository.save(any(EasyJournalLine.class))).thenAnswer(invocation -> {
      EasyJournalLine line = invocation.getArgument(0);
      linesByEntry.get(line.getEntry().getEntryId()).add(line);
      return line;
    });
    when(entryRepository.findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
        eq(1L), any(), any(), eq(EasyJournalStatus.POSTED)))
        .thenAnswer(invocation -> new ArrayList<>(entries.values()));
    when(lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(any()))
        .thenAnswer(invocation -> new ArrayList<>(
            linesByEntry.getOrDefault((Long) invocation.getArgument(0), List.of())));
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(eq(1L), anyInt()))
        .thenReturn(Optional.empty());
  }

  private EasyAccount account(Long id, String code, String name,
      EasyAccountDirection direction) {
    EasyAccount account = EasyAccount.builder().code(code).name(name)
        .direction(direction).sortOrder(1).active(true).build();
    ReflectionTestUtils.setField(account, "accountId", id);
    return account;
  }

  private void closeYear(int year) {
    EasyAnnualClosing closing = EasyAnnualClosing.builder()
        .business(academy).fiscalYear(year).build();
    closing.close("{}", "연마감");
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, year))
        .thenReturn(Optional.of(closing));
  }

  // ---------- A. GL ----------

  @Test
  @DisplayName("A1. 카드매입 → 비용 GL 차변 + 미지급금 GL 대변")
  void gl_cardPurchase_bothSides() {
    postingService.postCardPurchase(1L, LocalDate.of(2026, 9, 10), 11L, 12L, 30000L,
        "문구점", "문구", EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:1");

    AccountLedger expenseGl = inquiryService.generalLedger(1L, 11L, null, null);
    assertThat(expenseGl.periodDebit()).isEqualTo(30000L);
    assertThat(expenseGl.periodCredit()).isZero();
    assertThat(expenseGl.closingBalance()).isEqualTo(30000L);
    assertThat(expenseGl.rows()).hasSize(1);
    assertThat(expenseGl.rows().get(0).runningBalance()).isEqualTo(30000L);

    AccountLedger payableGl = inquiryService.generalLedger(1L, 12L, null, null);
    assertThat(payableGl.periodCredit()).isEqualTo(30000L);
    assertThat(payableGl.closingBalance()).isEqualTo(30000L);
  }

  @Test
  @DisplayName("A2/A3. 계좌이체 원장 + running 잔액")
  void gl_bankTransfer_runningBalance() {
    postingService.postBankTransfer(1L, LocalDate.of(2026, 8, 1), 13L, 14L, 5000000L, "이동1");
    postingService.postBankTransfer(1L, LocalDate.of(2026, 8, 2), 13L, 14L, 1000000L, "이동2");

    AccountLedger kbGl = inquiryService.generalLedger(1L, 14L, null, null);
    assertThat(kbGl.rows()).hasSize(2);
    assertThat(kbGl.rows().get(0).runningBalance()).isEqualTo(5000000L);
    assertThat(kbGl.rows().get(1).runningBalance()).isEqualTo(6000000L);
    assertThat(kbGl.closingBalance()).isEqualTo(6000000L);

    AccountLedger shinhanGl = inquiryService.generalLedger(1L, 13L, null, null);
    assertThat(shinhanGl.closingBalance()).isEqualTo(-6000000L);
  }

  // ---------- B. TB ----------

  @Test
  @DisplayName("B4/B5/B6. 시산표 균형 + 정상잔액 부호")
  void trialBalance_balancedWithNormalBalances() {
    postingService.postCardPurchase(1L, LocalDate.of(2026, 9, 10), 11L, 12L, 30000L,
        "문구점", "문구", EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:1");
    postingService.postLoanDisbursement(1L, LocalDate.of(2026, 1, 5), 13L, 15L,
        10000000L, "대출");

    TrialBalanceReport report = inquiryService.trialBalance(1L, null, null);
    assertThat(report.balanced()).isTrue();
    assertThat(report.totalDebit()).isEqualTo(report.totalCredit());
    assertThat(report.totalDebit()).isEqualTo(10030000L);
    assertThat(report.difference()).isZero();
    assertThat(report.mismatchedEntryIds()).isEmpty();
    // EXPENSE 차변 정상(+), LIABILITY 대변 정상(+), ASSET 차변 정상(+)
    assertThat(report.rows()).anySatisfy(row -> {
      if (row.accountCode().equals("EXPENSE_SUPPLIES")) {
        assertThat(row.endingBalance()).isEqualTo(30000L);
      }
    });
    assertThat(report.rows()).anySatisfy(row -> {
      if (row.accountCode().equals("LOAN_PAYABLE")) {
        assertThat(row.endingBalance()).isEqualTo(10000000L);
      }
    });
  }

  @Test
  @DisplayName("B-audit. 분개 균형 감사 read-only")
  void balanceAudit_allBalanced() {
    postingService.postCardPurchase(1L, LocalDate.of(2026, 9, 10), 11L, 12L, 30000L,
        "문구점", "문구", EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:1");

    BalanceAudit audit = inquiryService.auditBalance(1L, null, null);
    assertThat(audit.totalEntries()).isEqualTo(1);
    assertThat(audit.balancedEntries()).isEqualTo(1);
    assertThat(audit.unbalancedEntryIds()).isEmpty();
  }

  // ---------- C. PERIOD CLOSE ----------

  @Test
  @DisplayName("C7. OPEN 기간 전기 PASS")
  void openPeriod_postingPasses() {
    postingService.postCardPurchase(1L, LocalDate.of(2026, 9, 10), 11L, 12L, 30000L,
        "문구점", "문구", EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:1");
    assertThat(entries).hasSize(1);
  }

  @Test
  @DisplayName("C8. CLOSED 기간 카드매입 BLOCK")
  void closedPeriod_cardPurchaseBlocked() {
    closeYear(2026);

    assertThatThrownBy(() -> postingService.postCardPurchase(1L,
        LocalDate.of(2026, 9, 10), 11L, 12L, 30000L, "문구점", "문구",
        EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:1"))
        .isInstanceOf(EasyAccountingPeriodClosedException.class)
        .hasMessageContaining("ACCOUNTING_PERIOD_CLOSED");
    assertThat(entries).isEmpty();
  }

  @Test
  @DisplayName("C9/C10. CLOSED 기간 대출·이체 BLOCK")
  void closedPeriod_loanAndTransferBlocked() {
    closeYear(2026);

    assertThatThrownBy(() -> postingService.postLoanDisbursement(1L,
        LocalDate.of(2026, 1, 5), 13L, 15L, 10000000L, "대출"))
        .isInstanceOf(EasyAccountingPeriodClosedException.class);
    assertThatThrownBy(() -> postingService.postBankTransfer(1L,
        LocalDate.of(2026, 8, 1), 13L, 14L, 1000000L, "이체"))
        .isInstanceOf(EasyAccountingPeriodClosedException.class);
    assertThat(entries).isEmpty();
    verify(entryRepository, never()).save(any(EasyJournalEntry.class));
  }

  @Test
  @DisplayName("C11. reopen 후 전기 PASS (다른 연도는 영향 없음)")
  void otherYear_notBlockedByClosedYear() {
    closeYear(2026);

    postingService.postCardPurchase(1L, LocalDate.of(2027, 1, 5), 11L, 12L, 10000L,
        "문구점", "문구", EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:9");
    assertThat(entries).hasSize(1);
  }

  // ---------- D. OPENING ----------

  @Test
  @DisplayName("D12. 균형 개시분개 PASS + DRAFT cut-over")
  void opening_balancedCreatesDraftCutover() {
    when(cutoverRepository.findByBusiness_BusinessId(1L)).thenReturn(Optional.empty());
    when(cutoverRepository.save(any(EasyCutover.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EasyCutover cutover = openingService.postOpening(1L, LocalDate.of(2026, 1, 1),
        List.of(new OpeningLeg(13L, 5000000L, 0L), new OpeningLeg(15L, 0L, 5000000L)),
        "2026 개시잔액");

    assertThat(cutover.getStatus()).isEqualTo(EasyCutoverStatus.DRAFT);
    assertThat(cutover.getOpeningEntry()).isNotNull();
    TrialBalanceReport report = inquiryService.trialBalance(1L, null, null);
    assertThat(report.balanced()).isTrue();
  }

  @Test
  @DisplayName("D13. 불균형 개시분개 ROLLBACK")
  void opening_unbalancedRollback() {
    when(cutoverRepository.findByBusiness_BusinessId(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> openingService.postOpening(1L, LocalDate.of(2026, 1, 1),
        List.of(new OpeningLeg(13L, 5000000L, 0L), new OpeningLeg(15L, 0L, 4000000L)),
        "불균형"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(entries).isEmpty();
  }

  @Test
  @DisplayName("D14. 개시분개 SETTLEMENT BLOCK")
  void opening_settlementBlocked() {
    when(cutoverRepository.findByBusiness_BusinessId(1L)).thenReturn(Optional.empty());
    when(accountRepository.findById(17L)).thenReturn(Optional.of(settlement));

    assertThatThrownBy(() -> openingService.postOpening(1L, LocalDate.of(2026, 1, 1),
        List.of(new OpeningLeg(11L, 30000L, 0L), new OpeningLeg(17L, 0L, 30000L)),
        "구방식"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SETTLEMENT");
    assertThat(entries).isEmpty();
  }

  // ---------- E. HISTORICAL ----------

  @Test
  @DisplayName("E15/E16. 기존 repost 경로 불변 — SETTLEMENT 분개 유지")
  void legacyRepost_untouched() {
    when(accountRepository.findByCode("SETTLEMENT")).thenReturn(Optional.of(settlement));
    EasyTransaction txn = EasyTransaction.builder()
        .business(academy).occurredOn(LocalDate.of(2025, 3, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .category("소모품").account(supplies).build();
    ReflectionTestUtils.setField(txn, "txnId", 99L);
    when(entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(
        99L, EasyJournalStatus.POSTED)).thenReturn(List.of());

    journalService.repost(txn);

    assertThat(entries).hasSize(1);
    List<EasyJournalLine> saved = linesByEntry.values().iterator().next();
    assertThat(saved).hasSize(2);
    assertThat(saved.get(1).getAccount().getCode()).isEqualTo("SETTLEMENT");
  }
}
