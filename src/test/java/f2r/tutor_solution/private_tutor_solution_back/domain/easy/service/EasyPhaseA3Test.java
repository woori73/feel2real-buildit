package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAnnualClosing;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCutover;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTaxClassification;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCutoverRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialStatementService.BalanceSheet;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialStatementService.IncomeStatement;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyJournalService.Leg;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.AccountLedger;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceReport;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyOpeningService.OpeningLeg;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyReferenceYearSnapshot.ComparisonStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyReferenceYearSnapshot.ReferenceComparison;
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
@DisplayName("Easy Phase A3 세무기준·재무제표 테스트")
class EasyPhaseA3Test {

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
  private EasyFinancialStatementService statementService;
  private EasyReferenceYearSnapshot referenceSnapshot;

  private EasyBusiness academy;
  private EasyBusiness publisher;
  private EasyAccount tuition;
  private EasyAccount supplies;
  private EasyAccount cardPay;
  private EasyAccount bankShinhan;
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
    statementService = new EasyFinancialStatementService(inquiryService);
    referenceSnapshot = new EasyReferenceYearSnapshot();

    academy = business(1L, "ACADEMY", "학원");
    publisher = business(2L, "PUBLISHING", "출판사");
    tuition = account(10L, "INCOME_TUITION", "수강료", EasyAccountDirection.INCOME);
    supplies = account(11L, "EXPENSE_SUPPLIES", "소모품", EasyAccountDirection.EXPENSE);
    cardPay = account(12L, "CARD_PAY_SHINHAN", "미지급금-신한카드",
        EasyAccountDirection.LIABILITY);
    bankShinhan = account(13L, "BANK_SHINHAN", "보통예금-신한", EasyAccountDirection.ASSET);
    settlement = account(17L, "SETTLEMENT", "자금정산", EasyAccountDirection.ASSET);

    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(publisher));
    for (EasyAccount account : List.of(tuition, supplies, cardPay, bankShinhan, settlement)) {
      when(accountRepository.findById((Long) ReflectionTestUtils.getField(account, "accountId")))
          .thenReturn(Optional.of(account));
    }
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
        any(), any(), any(), eq(EasyJournalStatus.POSTED)))
        .thenAnswer(invocation -> {
          Long businessId = invocation.getArgument(0);
          List<EasyJournalEntry> result = new ArrayList<>();
          for (EasyJournalEntry entry : entries.values()) {
            if (entry.getBusiness().getBusinessId().equals(businessId)) {
              result.add(entry);
            }
          }
          return result;
        });
    when(lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(any()))
        .thenAnswer(invocation -> new ArrayList<>(
            linesByEntry.getOrDefault((Long) invocation.getArgument(0), List.of())));
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(any(), anyInt()))
        .thenReturn(Optional.empty());
    when(cutoverRepository.save(any(EasyCutover.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private EasyBusiness business(Long id, String code, String name) {
    EasyBusiness business = EasyBusiness.builder().code(code).name(name)
        .active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(business, "businessId", id);
    return business;
  }

  private EasyAccount account(Long id, String code, String name,
      EasyAccountDirection direction) {
    EasyAccount account = EasyAccount.builder().code(code).name(name)
        .direction(direction).sortOrder(1).active(true).build();
    ReflectionTestUtils.setField(account, "accountId", id);
    return account;
  }

  private void postAcademyPurchase() {
    postingService.postCardPurchase(1L, LocalDate.of(2026, 9, 10), 11L, 12L, 30000L,
        "문구점", "문구", EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:1");
  }

  // ---------- 1/2/14. 사업장 분리 ----------

  @Test
  @DisplayName("1/2/14. 학원·출판사 장부 분리")
  void businessIsolation() {
    postAcademyPurchase();
    postingService.postCardPurchase(2L, LocalDate.of(2026, 9, 11), 11L, 12L, 50000L,
        "서점", "도서", EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:2");

    TrialBalanceReport academyTb = inquiryService.trialBalance(1L, null, null);
    TrialBalanceReport publisherTb = inquiryService.trialBalance(2L, null, null);
    assertThat(academyTb.totalDebit()).isEqualTo(30000L);
    assertThat(publisherTb.totalDebit()).isEqualTo(50000L);

    IncomeStatement academyPl = statementService.incomeStatement(1L, null, null);
    IncomeStatement publisherPl = statementService.businessIncome(2L, null, null);
    assertThat(academyPl.expense()).isEqualTo(30000L);
    assertThat(publisherPl.expense()).isEqualTo(50000L);
  }

  // ---------- 3. 카드매입/대금 중복 방지 ----------

  @Test
  @DisplayName("3. 카드대금은 비용으로 잡히지 않는다")
  void cardPayment_noDoubleExpense() {
    postAcademyPurchase();
    postingService.postCardPayment(1L, LocalDate.of(2026, 9, 25), 12L, 13L, 30000L, "대금");

    IncomeStatement pl = statementService.incomeStatement(1L, null, null);
    assertThat(pl.expense()).isEqualTo(30000L);
    assertThat(pl.netIncome()).isEqualTo(-30000L);
    TrialBalanceReport trial = inquiryService.trialBalance(1L, null, null);
    assertThat(trial.balanced()).isTrue();
  }

  // ---------- 4/5/8. GL/TB/P&L 일관성 ----------

  @Test
  @DisplayName("4/5/8. GL=TB, P&L=수익-비용")
  void glTbPlConsistency() {
    postAcademyPurchase();
    journalService.postLegs(academy, LocalDate.of(2026, 9, 1), null, "수강료",
        List.of(new Leg(bankShinhan, 540000L, 0L), new Leg(tuition, 0L, 540000L)));

    AccountLedger suppliesGl = inquiryService.generalLedger(1L, 11L, null, null);
    TrialBalanceReport trial = inquiryService.trialBalance(1L, null, null);
    long tbSupplies = trial.rows().stream()
        .filter(row -> row.accountCode().equals("EXPENSE_SUPPLIES"))
        .mapToLong(row -> row.endingBalance()).sum();
    assertThat(suppliesGl.closingBalance()).isEqualTo(tbSupplies);

    IncomeStatement pl = statementService.incomeStatement(1L, null, null);
    assertThat(pl.revenue()).isEqualTo(540000L);
    assertThat(pl.expense()).isEqualTo(30000L);
    assertThat(pl.netIncome()).isEqualTo(510000L);
    assertThat(pl.trialBalanced()).isTrue();
  }

  // ---------- 6/7. B/S ----------

  @Test
  @DisplayName("6/7. B/S journal-source + equation (미결산 손익 분리 표시)")
  void balanceSheet_equationExplained() {
    postAcademyPurchase();
    journalService.postLegs(academy, LocalDate.of(2026, 9, 1), null, "수강료",
        List.of(new Leg(bankShinhan, 540000L, 0L), new Leg(tuition, 0L, 540000L)));

    BalanceSheet bs = statementService.balanceSheet(1L, null);
    assertThat(bs.assets()).isEqualTo(540000L);
    assertThat(bs.liabilities()).isEqualTo(30000L);
    assertThat(bs.equity()).isZero();
    assertThat(bs.unclosedNetIncome()).isEqualTo(510000L);
    // 결산 대체분개가 없으므로 strict equation은 손익만큼 차이 — 숨기지 않고 표시
    assertThat(bs.difference()).isEqualTo(510000L);
    assertThat(bs.balanced()).isFalse();
    assertThat(bs.balancedIncludingUnclosed()).isTrue();
  }

  // ---------- 9/10. 기준연도 비교 ----------

  @Test
  @DisplayName("9. 기준연도 비교 read-only (MATCH/MISMATCH/NOT_IMPORTED/MISSING)")
  void referenceComparison_readOnly() {
    int before = entries.size();

    ReferenceComparison match = referenceSnapshot.compare("ACADEMY", 2024, 64598500L, 63250576L);
    assertThat(match.status()).isEqualTo(ComparisonStatus.MATCH);

    ReferenceComparison mismatch = referenceSnapshot.compare("ACADEMY", 2024, 1L, 2L);
    assertThat(mismatch.status()).isEqualTo(ComparisonStatus.MISMATCH);

    ReferenceComparison notImported = referenceSnapshot.compare("ACADEMY", 2024, null, null);
    assertThat(notImported.status()).isEqualTo(ComparisonStatus.NOT_IMPORTED);

    ReferenceComparison missing = referenceSnapshot.compare("ACADEMY", 2030, 1L, 1L);
    assertThat(missing.status()).isEqualTo(ComparisonStatus.MISSING_SOURCE);

    assertThat(entries).hasSize(before);
  }

  @Test
  @DisplayName("10. 기준연도 수치는 변경 불가")
  void referenceData_immutable() {
    assertThatThrownBy(() -> referenceSnapshot.allReferences()
        .put("X|2000", null)).isInstanceOf(UnsupportedOperationException.class);
    assertThat(referenceSnapshot.reference("ACADEMY", 2024)).isPresent();
    assertThat(referenceSnapshot.reference("ACADEMY", 2024).get().netIncome())
        .isEqualTo(1347924L);
  }

  // ---------- 11/12. 마감·SETTLEMENT ----------

  @Test
  @DisplayName("11. 마감기간 차단 유지")
  void closedPeriod_blocked() {
    EasyAnnualClosing closing = EasyAnnualClosing.builder()
        .business(academy).fiscalYear(2026).build();
    closing.close("{}", "연마감");
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2026))
        .thenReturn(Optional.of(closing));

    assertThatThrownBy(() -> postingService.postCardPurchase(1L,
        LocalDate.of(2026, 9, 10), 11L, 12L, 30000L, "문구점", "문구",
        EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE, "card_event:1"))
        .isInstanceOf(EasyAccountingPeriodClosedException.class);
  }

  @Test
  @DisplayName("12. SETTLEMENT 신규 차단 유지")
  void settlementBlock_preserved() {
    assertThatThrownBy(() -> journalService.postLegs(academy, LocalDate.of(2026, 9, 10),
        null, "구방식",
        List.of(new Leg(supplies, 30000L, 0L), new Leg(settlement, 0L, 30000L))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SETTLEMENT");
  }

  // ---------- 13. 개시 이중계상 방지 ----------

  @Test
  @DisplayName("13. 개시분개는 TB에 1회만 반영 + cut-over 연결")
  void opening_noDoubleCount() {
    when(cutoverRepository.findByBusiness_BusinessId(1L)).thenReturn(Optional.empty());

    EasyCutover cutover = openingService.postOpening(1L, LocalDate.of(2026, 1, 1),
        List.of(new OpeningLeg(13L, 5000000L, 0L), new OpeningLeg(12L, 0L, 5000000L)),
        "2026 개시잔액");
    postAcademyPurchase();

    TrialBalanceReport trial = inquiryService.trialBalance(1L, null, null);
    assertThat(trial.balanced()).isTrue();
    assertThat(trial.totalDebit()).isEqualTo(5030000L);
    assertThat(cutover.getOpeningEntry()).isNotNull();
  }

  // ---------- 15. provenance ----------

  @Test
  @DisplayName("15. 원거래→분개→원장 추적 (sourceRef)")
  void provenance_traceable() {
    postAcademyPurchase();

    AccountLedger gl = inquiryService.generalLedger(1L, 11L, null, null);
    assertThat(gl.rows()).hasSize(1);
    assertThat(gl.rows().get(0).sourceRef()).isEqualTo("card_event:1");
    assertThat(gl.rows().get(0).journalEntryId()).isNotNull();
    assertThat(gl.rows().get(0).memo()).contains("문구");
  }
}
