package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFilingReferenceRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyYearPlResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("EasyReportService 결과 집계 테스트")
class EasyReportServiceTest {

  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyJournalEntryRepository entryRepository;
  @Mock EasyJournalLineRepository lineRepository;
  @Mock EasyFilingReferenceRepository filingRepository;
  @Mock EasyJournalService journalService;

  private EasyReportService service;
  private EasyBusiness academy;
  private EasyBusiness publishing;
  private EasyAccount tuition;
  private EasyAccount supplies;
  private EasyAccount settlement;

  @BeforeEach
  void setUp() {
    service = new EasyReportService(
        txnRepository, businessRepository, entryRepository, lineRepository,
        filingRepository, journalService);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    publishing = EasyBusiness.builder().code("PUBLISHING").name("출판사").active(true)
        .sortOrder(2).build();
    ReflectionTestUtils.setField(academy, "businessId", 1L);
    ReflectionTestUtils.setField(publishing, "businessId", 2L);
    tuition = EasyAccount.builder().code("INCOME_TUITION").name("수강료")
        .direction(EasyAccountDirection.INCOME).sortOrder(1).active(true).build();
    supplies = EasyAccount.builder().code("EXPENSE_SUPPLIES").name("소모품")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(16).active(true).build();
    settlement = EasyAccount.builder().code("SETTLEMENT").name("자금정산")
        .direction(EasyAccountDirection.ASSET).sortOrder(0).active(true).build();
    ReflectionTestUtils.setField(tuition, "accountId", 10L);
    ReflectionTestUtils.setField(supplies, "accountId", 11L);
    ReflectionTestUtils.setField(settlement, "accountId", 12L);
  }

  private EasyTransaction txn(Long txnId, EasyBusiness business, LocalDate date,
      EasyTxnDirection direction, long amount, EasyAccount account) {
    EasyTransaction txn = EasyTransaction.builder()
        .business(business).occurredOn(date).direction(direction)
        .amount(amount).category("분류").account(account).build();
    ReflectionTestUtils.setField(txn, "txnId", txnId);
    return txn;
  }

  private EasyJournalEntry entry(Long entryId, EasyBusiness business, LocalDate date) {
    EasyJournalEntry entry = EasyJournalEntry.builder()
        .business(business).occurredOn(date).build();
    ReflectionTestUtils.setField(entry, "entryId", entryId);
    return entry;
  }

  private EasyJournalLine line(EasyJournalEntry entry, EasyAccount account,
      long debit, long credit, int order) {
    return EasyJournalLine.builder()
        .entry(entry).account(account).debit(debit).credit(credit).sortOrder(order).build();
  }

  @Test
  @DisplayName("연도 손익 — 분개 기준 + 장부 교차검증 + 월별 + 계정별")
  void yearPl_matchesLedger() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    EasyTransaction marIncome = txn(11L, academy, LocalDate.of(2025, 3, 5),
        EasyTxnDirection.INCOME, 300000L, tuition);
    EasyTransaction marExpense = txn(12L, academy, LocalDate.of(2025, 3, 6),
        EasyTxnDirection.EXPENSE, 50000L, supplies);
    EasyTransaction junIncome = txn(13L, academy, LocalDate.of(2025, 6, 1),
        EasyTxnDirection.INCOME, 100000L, tuition);
    List<EasyTransaction> all = List.of(marIncome, marExpense, junIncome);
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        eq(1L), any(), any()))
        .thenAnswer(invocation -> all.stream()
            .filter(txn -> !txn.getOccurredOn().isBefore(invocation.getArgument(1))
                && !txn.getOccurredOn().isAfter(invocation.getArgument(2)))
            .toList());
    EasyJournalEntry e1 = entry(91L, academy, LocalDate.of(2025, 3, 5));
    EasyJournalEntry e2 = entry(92L, academy, LocalDate.of(2025, 3, 6));
    EasyJournalEntry e3 = entry(93L, academy, LocalDate.of(2025, 6, 1));
    when(entryRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
            eq(1L), any(), any(), eq(EasyJournalStatus.POSTED)))
        .thenReturn(List.of(e1, e2, e3));
    when(lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(91L)).thenReturn(List.of(
        line(e1, settlement, 300000L, 0L, 0), line(e1, tuition, 0L, 300000L, 1)));
    when(lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(92L)).thenReturn(List.of(
        line(e2, supplies, 50000L, 0L, 0), line(e2, settlement, 0L, 50000L, 1)));
    when(lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(93L)).thenReturn(List.of(
        line(e3, settlement, 100000L, 0L, 0), line(e3, tuition, 0L, 100000L, 1)));
    when(journalService.trialBalance(eq(1L), any(), any()))
        .thenReturn(new TrialBalance(450000L, 450000L));
    when(entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(any(),
        eq(EasyJournalStatus.POSTED)))
        .thenAnswer(invocation -> List.of(e1));

    EasyYearPlResponse pl = service.yearPl(1L, 2025);

    assertThat(pl.income()).isEqualTo(400000L);
    assertThat(pl.expense()).isEqualTo(50000L);
    assertThat(pl.profit()).isEqualTo(350000L);
    assertThat(pl.journalIncome()).isEqualTo(400000L);
    assertThat(pl.journalExpense()).isEqualTo(50000L);
    assertThat(pl.journalBalanced()).isTrue();
    assertThat(pl.matchesLedger()).isTrue();
    assertThat(pl.unpostedTxnCount()).isZero();
    assertThat(pl.monthly().get(2).income()).isEqualTo(300000L);
    assertThat(pl.monthly().get(2).expense()).isEqualTo(50000L);
    assertThat(pl.monthly().get(5).income()).isEqualTo(100000L);
    assertThat(pl.byAccount().get(0).accountName()).isEqualTo("수강료");
    assertThat(pl.byAccount().get(0).total()).isEqualTo(400000L);
    assertThat(pl.prevYear().income()).isZero();
    assertThat(pl.prevYear().incomeRate()).isNull();
  }

  @Test
  @DisplayName("신고자료 대조 — 일치/차이/기준없음을 구분, 역산 없음")
  void filingCheck_lines() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    List<EasyTransaction> all = List.of(
        txn(11L, academy, LocalDate.of(2025, 3, 5), EasyTxnDirection.INCOME, 400000L, tuition),
        txn(12L, academy, LocalDate.of(2025, 3, 6), EasyTxnDirection.EXPENSE, 50000L, supplies));
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        eq(1L), any(), any()))
        .thenAnswer(invocation -> all.stream()
            .filter(txn -> !txn.getOccurredOn().isBefore(invocation.getArgument(1))
                && !txn.getOccurredOn().isAfter(invocation.getArgument(2)))
            .toList());
    EasyFilingReference ref = new EasyFilingReference(academy, 2025, 400000L, 60000L, 340000L,
        "Library 종합소득세 신고서", null);
    when(filingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2025))
        .thenReturn(Optional.of(ref));

    var check = service.filingCheck(1L, 2025);

    assertThat(check.ledgerRevenue()).isEqualTo(400000L);
    assertThat(check.lines().get(0).cause()).isEqualTo("MATCHED");
    assertThat(check.lines().get(1).cause()).isEqualTo("NEEDS_REVIEW");
    assertThat(check.lines().get(1).diff()).isEqualTo(-10000L);
    assertThat(check.lines().get(2).cause()).isEqualTo("NEEDS_REVIEW");

    when(filingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2024))
        .thenReturn(Optional.empty());
    var unknown = service.filingCheck(1L, 2024);
    assertThat(unknown.lines()).allMatch(line -> "UNKNOWN".equals(line.cause()));

    assertThatThrownBy(() -> service.filingCheck(null, 2025))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("종소세 준비 — 사업장별 분리 합산, 결정세액 미표시")
  void incomeTax_combinedPreparing() {
    when(businessRepository.findAll()).thenReturn(List.of(academy, publishing));
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        eq(1L), any(), any()))
        .thenReturn(List.of(
            txn(11L, academy, LocalDate.of(2025, 3, 5), EasyTxnDirection.INCOME, 400000L, tuition),
            txn(12L, academy, LocalDate.of(2025, 3, 6), EasyTxnDirection.EXPENSE, 50000L,
                supplies)));
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        eq(2L), any(), any()))
        .thenReturn(List.of(
            txn(21L, publishing, LocalDate.of(2025, 4, 1), EasyTxnDirection.INCOME, 200000L,
                tuition),
            txn(22L, publishing, LocalDate.of(2025, 4, 2), EasyTxnDirection.EXPENSE, 20000L,
                supplies)));
    EasyFilingReference ref = new EasyFilingReference(academy, 2025, 400000L, 50000L, 350000L,
        "Library", null);
    when(filingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2025))
        .thenReturn(Optional.of(ref));
    when(filingRepository.findByBusiness_BusinessIdAndFiscalYear(2L, 2025))
        .thenReturn(Optional.empty());

    var tax = service.incomeTax(2025);

    assertThat(tax.slices()).hasSize(2);
    assertThat(tax.slices().get(0).primary()).isTrue();
    assertThat(tax.slices().get(0).checkCause()).isEqualTo("MATCHED");
    assertThat(tax.slices().get(1).checkCause()).isEqualTo("UNKNOWN");
    assertThat(tax.combinedRevenue()).isEqualTo(600000L);
    assertThat(tax.combinedExpense()).isEqualTo(70000L);
    assertThat(tax.combinedProfit()).isEqualTo(530000L);
    assertThat(tax.status()).isEqualTo("PREPARING");
  }

  @Test
  @DisplayName("신고 기준자료 upsert — 사업체·연도 검증 + 중복 연도 갱신")
  void upsertReference_validatesAndUpdates() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(filingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2025))
        .thenReturn(Optional.empty());
    when(filingRepository.save(any(EasyFilingReference.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var created = service.upsertReference(new EasyFilingReferenceRequest(
        1L, 2025, 400000L, 50000L, 350000L, "Library", null));
    assertThat(created.revenue()).isEqualTo(400000L);

    assertThatThrownBy(() -> service.upsertReference(new EasyFilingReferenceRequest(
        null, 2025, 1L, 1L, 1L, "", null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.upsertReference(new EasyFilingReferenceRequest(
        1L, 1999, 1L, 1L, 1L, "", null)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
