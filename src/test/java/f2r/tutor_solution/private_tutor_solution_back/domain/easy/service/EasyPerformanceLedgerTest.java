package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyPerformanceService.GroupSeries;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyPerformanceService.PerformanceReport;
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
@DisplayName("Easy 장부요약/실적관리 테스트 (POSTED journal 기준)")
class EasyPerformanceLedgerTest {

  @Mock EasyJournalEntryRepository entryRepository;
  @Mock EasyJournalLineRepository lineRepository;
  @Mock EasyAccountRepository accountRepository;
  @Mock EasyBusinessRepository businessRepository;

  private EasyLedgerInquiryService inquiryService;
  private EasyFinancialStatementService statementService;
  private EasyPerformanceService performanceService;

  private EasyBusiness academy;
  private EasyBusiness publishing;
  private final Map<String, EasyAccount> accounts = new LinkedHashMap<>();
  private final Map<Long, EasyJournalEntry> entries = new LinkedHashMap<>();
  private final Map<Long, List<EasyJournalLine>> linesByEntry = new LinkedHashMap<>();
  private final AtomicLong entryIds = new AtomicLong(1L);
  private final AtomicLong accountIds = new AtomicLong(100L);

  @BeforeEach
  void setUp() {
    inquiryService = new EasyLedgerInquiryService(entryRepository, lineRepository,
        accountRepository);
    statementService = new EasyFinancialStatementService(inquiryService);
    performanceService = new EasyPerformanceService(inquiryService, statementService,
        businessRepository, accountRepository);

    academy = business(1L, "ACADEMY");
    publishing = business(2L, "PUBLISHING");
    when(businessRepository.findAllByActiveTrueOrderBySortOrderAsc())
        .thenReturn(List.of(academy, publishing));

    account("INCOME_TUITION", EasyAccountDirection.INCOME);
    account("INCOME_BOOKS", EasyAccountDirection.INCOME);
    account("INCOME_OTHER", EasyAccountDirection.INCOME);
    account("EXPENSE_SALARY", EasyAccountDirection.EXPENSE);
    account("EXPENSE_RENT", EasyAccountDirection.EXPENSE);
    account("EXPENSE_ADMIN", EasyAccountDirection.EXPENSE);
    account("EXPENSE_PRINT", EasyAccountDirection.EXPENSE);
    account("EXPENSE_AD", EasyAccountDirection.EXPENSE);
    account("EXPENSE_SUPPLIES", EasyAccountDirection.EXPENSE);
    account("EXPENSE_FEE", EasyAccountDirection.EXPENSE);
    account("EXPENSE_TAX", EasyAccountDirection.EXPENSE);
    account("EXPENSE_ENTERTAIN", EasyAccountDirection.EXPENSE);
    account("EXPENSE_INSURANCE", EasyAccountDirection.EXPENSE);
    account("EXPENSE_TRAVEL", EasyAccountDirection.EXPENSE);
    account("EXPENSE_COMMUNICATION", EasyAccountDirection.EXPENSE);
    account("EXPENSE_OTHER", EasyAccountDirection.EXPENSE);
    // P/L이 아닌 계정 — 실적에 포함되면 안 된다.
    account("BANK_SHINHAN", EasyAccountDirection.ASSET);
    account("CARD_PAYABLE", EasyAccountDirection.ASSET);
    account("LOAN_PAYABLE", EasyAccountDirection.ASSET);

    // given: 2026-06 학원 — 수강료 5,000,000(카드) + 임차료 3,300,000
    post(1L, LocalDate.of(2026, 6, 5), "INCOME_TUITION", 0, 5_000_000,
        "BANK_SHINHAN", 5_000_000, 0, txn("학부모", "6월 수강료"));
    post(1L, LocalDate.of(2026, 6, 25), "EXPENSE_RENT", 3_300_000, 0,
        "BANK_SHINHAN", 0, 3_300_000, txn("엄선경", "6월 임차료"));
    // given: 2026-07 학원 — 수강료 4,000,000 + 소모품 200,000(카드매입)
    post(1L, LocalDate.of(2026, 7, 5), "INCOME_TUITION", 0, 4_000_000,
        "BANK_SHINHAN", 4_000_000, 0, txn("학부모", "7월 수강료"));
    post(1L, LocalDate.of(2026, 7, 10), "EXPENSE_SUPPLIES", 200_000, 0,
        "CARD_PAYABLE", 0, 200_000, txn("문구점", "문구"));
    // given: 카드대금 결제 200,000 — 비용 중복이 되면 안 된다 (ASSET↔ASSET)
    post(1L, LocalDate.of(2026, 7, 25), "CARD_PAYABLE", 200_000, 0,
        "BANK_SHINHAN", 0, 200_000, txn("신한카드", "7월 카드대금"));
    // given: 대출 입금 10,000,000 — 수입이 되면 안 된다 (ASSET↔ASSET)
    post(1L, LocalDate.of(2026, 7, 26), "BANK_SHINHAN", 10_000_000, 0,
        "LOAN_PAYABLE", 0, 10_000_000, txn("은행", "대출 입금"));
    // given: 출판사 2026-06 — 학원과 섞이면 안 된다
    post(2L, LocalDate.of(2026, 6, 5), "INCOME_BOOKS", 0, 1_000_000,
        "BANK_SHINHAN", 1_000_000, 0, txn("서점", "6월 교재매출"));

    when(entryRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
            eq(1L), any(), any(), eq(EasyJournalStatus.POSTED)))
        .thenAnswer(invocation -> filterEntries(1L,
            invocation.getArgument(1), invocation.getArgument(2)));
    when(entryRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
            eq(2L), any(), any(), eq(EasyJournalStatus.POSTED)))
        .thenAnswer(invocation -> filterEntries(2L,
            invocation.getArgument(1), invocation.getArgument(2)));
    when(lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(any()))
        .thenAnswer(invocation -> new ArrayList<>(
            linesByEntry.getOrDefault((Long) invocation.getArgument(0), List.of())));
    when(accountRepository.findById(any()))
        .thenAnswer(invocation -> accounts.values().stream()
            .filter(a -> a.getAccountId().equals(invocation.getArgument(0)))
            .findFirst());
    when(accountRepository.findByCode(any()))
        .thenAnswer(invocation -> accounts.values().stream()
            .filter(a -> a.getCode().equals(invocation.getArgument(0)))
            .findFirst());
  }

  @Test
  @DisplayName("12개월 matrix + 누계 + 월손익 + 누적손익")
  void performance_monthlyMatrix() {
    PerformanceReport report = performanceService.performance("ACADEMY", 2026);

    assertThat(report.businessCode()).isEqualTo("ACADEMY");
    assertThat(report.months()).hasSize(12);
    // 6월: 수입 5,000,000 / 비용 3,300,000 / 손익 1,700,000
    assertThat(report.months().get(5).income()).isEqualTo(5_000_000L);
    assertThat(report.months().get(5).expense()).isEqualTo(3_300_000L);
    assertThat(report.months().get(5).profit()).isEqualTo(1_700_000L);
    assertThat(report.months().get(5).cumulativeProfit()).isEqualTo(1_700_000L);
    // 7월: 수입 4,000,000 / 비용 200,000 / 카드대금·대출 제외
    assertThat(report.months().get(6).income()).isEqualTo(4_000_000L);
    assertThat(report.months().get(6).expense()).isEqualTo(200_000L);
    assertThat(report.months().get(6).profit()).isEqualTo(3_800_000L);
    assertThat(report.months().get(6).cumulativeProfit()).isEqualTo(5_500_000L);
    // 빈 월 = 0
    assertThat(report.months().get(0).income()).isZero();
    assertThat(report.months().get(0).expense()).isZero();
    assertThat(report.months().get(0).profit()).isZero();
    // 연간
    assertThat(report.annualIncome()).isEqualTo(9_000_000L);
    assertThat(report.annualExpense()).isEqualTo(3_500_000L);
    assertThat(report.annualProfit()).isEqualTo(5_500_000L);
    assertThat(report.annualTrialBalanced()).isTrue();
    assertThat(report.annualMatchesIncomeStatement()).isTrue();
  }

  @Test
  @DisplayName("수입/비용 그룹핑 — 계정별 행 + 누계")
  void performance_groups() {
    PerformanceReport report = performanceService.performance("ACADEMY", 2026);

    GroupSeries tuition = group(report.incomeGroups(), "TUITION");
    assertThat(tuition.monthly().get(5)).isEqualTo(5_000_000L);
    assertThat(tuition.monthly().get(6)).isEqualTo(4_000_000L);
    assertThat(tuition.total()).isEqualTo(9_000_000L);

    GroupSeries rent = group(report.expenseGroups(), "RENT");
    assertThat(rent.monthly().get(5)).isEqualTo(3_300_000L);
    assertThat(rent.total()).isEqualTo(3_300_000L);

    GroupSeries supplies = group(report.expenseGroups(), "SUPPLIES");
    assertThat(supplies.monthly().get(6)).isEqualTo(200_000L);

    GroupSeries comm = group(report.expenseGroups(), "COMM");
    assertThat(comm.accountCode()).isEqualTo("EXPENSE_COMMUNICATION");
    assertThat(comm.total()).isZero();
  }

  @Test
  @DisplayName("P/L 안전 — ASSET 분개(대출·카드대금·이체)는 실적 제외")
  void performance_plSafety() {
    PerformanceReport report = performanceService.performance("ACADEMY", 2026);

    // 대출 10,000,000이 수입에 포함되면 안 된다
    assertThat(report.annualIncome()).isEqualTo(9_000_000L);
    // 카드대금 200,000이 비용에 이중 집계되면 안 된다 (소모품 200,000만 1회)
    assertThat(report.annualExpense()).isEqualTo(3_500_000L);
  }

  @Test
  @DisplayName("사업장 격리 — 출판사 수치가 학원에 섞이지 않는다")
  void performance_isolation() {
    PerformanceReport academy = performanceService.performance("ACADEMY", 2026);
    assertThat(academy.annualIncome()).isEqualTo(9_000_000L);

    PerformanceReport publishing = performanceService.performance("PUBLISHING", 2026);
    assertThat(publishing.annualIncome()).isEqualTo(1_000_000L);
    assertThat(publishing.annualExpense()).isZero();
  }

  @Test
  @DisplayName("drill-down 합계 = 선택 셀 금액 + 추적 필드")
  void drillDown_matchesCell() {
    var ledger = inquiryService.generalLedger(1L,
        accounts.get("EXPENSE_RENT").getAccountId(),
        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

    assertThat(ledger.rows()).hasSize(1);
    assertThat(ledger.periodDebit()).isEqualTo(3_300_000L);
    assertThat(ledger.rows().get(0).counterparty()).isEqualTo("엄선경");
    assertThat(ledger.rows().get(0).description()).isEqualTo("6월 임차료");
    assertThat(ledger.rows().get(0).journalEntryId()).isNotNull();
    // 셀 금액(6월 임차료)과 drill-down 합계 일치
    PerformanceReport report = performanceService.performance("ACADEMY", 2026);
    assertThat(group(report.expenseGroups(), "RENT").monthly().get(5))
        .isEqualTo(ledger.periodDebit());
  }

  @Test
  @DisplayName("연간 손익계산서와 월 합계 내부 일관성")
  void performance_annualConsistency() {
    PerformanceReport report = performanceService.performance("ACADEMY", 2026);

    long monthlyIncomeSum = report.months().stream()
        .mapToLong(m -> m.income()).sum();
    long monthlyExpenseSum = report.months().stream()
        .mapToLong(m -> m.expense()).sum();
    assertThat(monthlyIncomeSum).isEqualTo(report.annualIncome());
    assertThat(monthlyExpenseSum).isEqualTo(report.annualExpense());
    assertThat(report.annualMatchesIncomeStatement()).isTrue();
  }

  // ---------- fixture ----------

  private EasyBusiness business(Long id, String code) {
    EasyBusiness business = EasyBusiness.builder().code(code).name(code)
        .active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(business, "businessId", id);
    return business;
  }

  private void account(String code, EasyAccountDirection direction) {
    EasyAccount account = EasyAccount.builder().code(code).name(code)
        .direction(direction).sortOrder(1).active(true).build();
    ReflectionTestUtils.setField(account, "accountId", accountIds.getAndIncrement());
    accounts.put(code, account);
  }

  private EasyTransaction txn(String counterparty, String description) {
    return EasyTransaction.builder().counterparty(counterparty).description(description)
        .build();
  }

  private void post(Long businessId, LocalDate date, String debitCode, long debit,
      long credit, String creditCode, long creditDebit, long creditCredit,
      EasyTransaction txn) {
    EasyBusiness business = businessId == 1L ? academy : publishing;
    EasyJournalEntry entry = EasyJournalEntry.builder().business(business)
        .occurredOn(date).txn(txn).memo(description(txn)).build();
    ReflectionTestUtils.setField(entry, "entryId", entryIds.getAndIncrement());
    entries.put(entry.getEntryId(), entry);
    List<EasyJournalLine> lines = new ArrayList<>();
    lines.add(EasyJournalLine.builder().entry(entry).account(accounts.get(debitCode))
        .debit(debit).credit(credit).sortOrder(1).build());
    lines.add(EasyJournalLine.builder().entry(entry).account(accounts.get(creditCode))
        .debit(creditDebit).credit(creditCredit).sortOrder(2).build());
    linesByEntry.put(entry.getEntryId(), lines);
  }

  private String description(EasyTransaction txn) {
    return txn == null ? null : txn.getDescription();
  }

  private List<EasyJournalEntry> filterEntries(Long businessId, LocalDate from,
      LocalDate to) {
    List<EasyJournalEntry> result = new ArrayList<>();
    for (EasyJournalEntry entry : entries.values()) {
      if (entry.getBusiness().getBusinessId().equals(businessId)
          && !entry.getOccurredOn().isBefore(from)
          && !entry.getOccurredOn().isAfter(to)) {
        result.add(entry);
      }
    }
    return result;
  }

  private GroupSeries group(List<GroupSeries> groups, String key) {
    return groups.stream().filter(g -> g.groupKey().equals(key)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("그룹 없음: " + key));
  }
}
