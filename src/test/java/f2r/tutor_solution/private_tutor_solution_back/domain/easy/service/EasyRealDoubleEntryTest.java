package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyJournalService.Leg;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTaxClassification;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Easy Phase A1 실계정 복식 테스트")
class EasyRealDoubleEntryTest {

  @Mock EasyJournalEntryRepository entryRepository;
  @Mock EasyJournalLineRepository lineRepository;
  @Mock EasyAccountRepository accountRepository;
  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyAnnualClosingRepository closingRepository;

  private EasyJournalService journalService;
  private EasyRealPostingService postingService;
  private EasyBusiness academy;

  private EasyAccount supplies;
  private EasyAccount cardPayShinhan;
  private EasyAccount bankShinhan;
  private EasyAccount bankKb;
  private EasyAccount loan;
  private EasyAccount interest;
  private EasyAccount settlement;

  @BeforeEach
  void setUp() {
    journalService = new EasyJournalService(entryRepository, lineRepository, accountRepository,
        closingRepository);
    postingService = new EasyRealPostingService(journalService, txnRepository,
        accountRepository, businessRepository);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(academy, "businessId", 1L);
    supplies = account(11L, "EXPENSE_SUPPLIES", "소모품", EasyAccountDirection.EXPENSE);
    cardPayShinhan = account(12L, "CARD_PAY_SHINHAN", "미지급금-신한카드",
        EasyAccountDirection.LIABILITY);
    bankShinhan = account(13L, "BANK_SHINHAN", "보통예금-신한", EasyAccountDirection.ASSET);
    bankKb = account(14L, "BANK_KB", "보통예금-KB", EasyAccountDirection.ASSET);
    loan = account(15L, "LOAN_PAYABLE", "차입금", EasyAccountDirection.LIABILITY);
    interest = account(16L, "EXPENSE_INTEREST", "이자비용", EasyAccountDirection.EXPENSE);
    settlement = account(17L, "SETTLEMENT", "자금정산", EasyAccountDirection.ASSET);
  }

  private EasyAccount account(Long id, String code, String name, EasyAccountDirection direction) {
    EasyAccount account = EasyAccount.builder().code(code).name(name)
        .direction(direction).sortOrder(1).active(true).build();
    ReflectionTestUtils.setField(account, "accountId", id);
    return account;
  }

  private void stubCommon() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(accountRepository.findById(11L)).thenReturn(Optional.of(supplies));
    when(accountRepository.findById(12L)).thenReturn(Optional.of(cardPayShinhan));
    when(accountRepository.findById(13L)).thenReturn(Optional.of(bankShinhan));
    when(accountRepository.findById(14L)).thenReturn(Optional.of(bankKb));
    when(accountRepository.findById(15L)).thenReturn(Optional.of(loan));
    when(accountRepository.findById(16L)).thenReturn(Optional.of(interest));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(entryRepository.save(any(EasyJournalEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(lineRepository.save(any(EasyJournalLine.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private List<EasyJournalLine> savedLines() {
    ArgumentCaptor<EasyJournalLine> captor = ArgumentCaptor.forClass(EasyJournalLine.class);
    verify(lineRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getAllValues();
  }

  @Test
  @DisplayName("1. 카드비용 — Dr 소모품 / Cr 미지급금-신한카드")
  void cardPurchase_postsRealAccounts() {
    stubCommon();

    postingService.postCardPurchase(1L, LocalDate.of(2026, 9, 10), 11L, 12L, 30000L,
        "문구점", "문구 30,000원", EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE,
        "card_event:1");

    List<EasyJournalLine> lines = savedLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).getAccount().getCode()).isEqualTo("EXPENSE_SUPPLIES");
    assertThat(lines.get(0).getDebit()).isEqualTo(30000L);
    assertThat(lines.get(0).getCredit()).isZero();
    assertThat(lines.get(1).getAccount().getCode()).isEqualTo("CARD_PAY_SHINHAN");
    assertThat(lines.get(1).getCredit()).isEqualTo(30000L);
    assertThat(lines.get(1).getDebit()).isZero();
    ArgumentCaptor<EasyTransaction> txnCaptor =
        ArgumentCaptor.forClass(EasyTransaction.class);
    verify(txnRepository).save(txnCaptor.capture());
    assertThat(txnCaptor.getValue().getTaxClassification())
        .isEqualTo(EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE);
  }

  @Test
  @DisplayName("2. 카드대금 — Dr 미지급금 / Cr 지정 은행")
  void cardPayment_linksPayableToBank() {
    stubCommon();

    postingService.postCardPayment(1L, LocalDate.of(2026, 9, 25), 12L, 13L, 1026892L,
        "신한카드 9월 대금");

    List<EasyJournalLine> lines = savedLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).getAccount().getCode()).isEqualTo("CARD_PAY_SHINHAN");
    assertThat(lines.get(0).getDebit()).isEqualTo(1026892L);
    assertThat(lines.get(1).getAccount().getCode()).isEqualTo("BANK_SHINHAN");
    assertThat(lines.get(1).getCredit()).isEqualTo(1026892L);
  }

  @Test
  @DisplayName("3. 대출입금 — Dr 은행 / Cr 차입금 (P&L 거래 없음)")
  void loanDisbursement_notIncome() {
    stubCommon();

    postingService.postLoanDisbursement(1L, LocalDate.of(2026, 1, 5), 13L, 15L,
        10000000L, "운영자금 대출");

    List<EasyJournalLine> lines = savedLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).getAccount().getCode()).isEqualTo("BANK_SHINHAN");
    assertThat(lines.get(0).getDebit()).isEqualTo(10000000L);
    assertThat(lines.get(1).getAccount().getCode()).isEqualTo("LOAN_PAYABLE");
    assertThat(lines.get(1).getCredit()).isEqualTo(10000000L);
    verify(txnRepository, never()).save(any(EasyTransaction.class));
  }

  @Test
  @DisplayName("4. 대출원금 상환 — Dr 차입금 / Cr 은행 (P&L 거래 없음)")
  void loanPrincipal_notExpense() {
    stubCommon();

    postingService.postLoanPrincipalRepayment(1L, LocalDate.of(2026, 2, 5), 15L, 13L,
        500000L, "원금 상환");

    List<EasyJournalLine> lines = savedLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).getAccount().getCode()).isEqualTo("LOAN_PAYABLE");
    assertThat(lines.get(0).getDebit()).isEqualTo(500000L);
    assertThat(lines.get(1).getAccount().getCode()).isEqualTo("BANK_SHINHAN");
    assertThat(lines.get(1).getCredit()).isEqualTo(500000L);
    verify(txnRepository, never()).save(any(EasyTransaction.class));
  }

  @Test
  @DisplayName("5. 대출이자 — Dr 이자비용 / Cr 은행, 세무구분 후보")
  void loanInterest_deductibleCandidate() {
    stubCommon();

    postingService.postLoanInterest(1L, LocalDate.of(2026, 2, 5), 16L, 13L,
        45000L, "2월 대출이자", "bank:2026-02");

    List<EasyJournalLine> lines = savedLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).getAccount().getCode()).isEqualTo("EXPENSE_INTEREST");
    assertThat(lines.get(0).getDebit()).isEqualTo(45000L);
    assertThat(lines.get(1).getAccount().getCode()).isEqualTo("BANK_SHINHAN");
    ArgumentCaptor<EasyTransaction> txnCaptor =
        ArgumentCaptor.forClass(EasyTransaction.class);
    verify(txnRepository).save(txnCaptor.capture());
    assertThat(txnCaptor.getValue().getTaxClassification())
        .isEqualTo(EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE);
  }

  @Test
  @DisplayName("6. 계좌이체 — Dr KB / Cr 신한 (수익·비용 아님)")
  void bankTransfer_betweenOwnAccounts() {
    stubCommon();

    postingService.postBankTransfer(1L, LocalDate.of(2026, 8, 1), 13L, 14L,
        5000000L, "주거래 이동");

    List<EasyJournalLine> lines = savedLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).getAccount().getCode()).isEqualTo("BANK_KB");
    assertThat(lines.get(0).getDebit()).isEqualTo(5000000L);
    assertThat(lines.get(1).getAccount().getCode()).isEqualTo("BANK_SHINHAN");
    assertThat(lines.get(1).getCredit()).isEqualTo(5000000L);
    verify(txnRepository, never()).save(any(EasyTransaction.class));
  }

  @Test
  @DisplayName("7. 불균형 분개 — 거부 + 저장 없음(롤백)")
  void unbalancedLegs_rejectedWithoutSave() {
    when(entryRepository.save(any(EasyJournalEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> journalService.postLegs(academy,
        LocalDate.of(2026, 9, 10), null, "불균형",
        List.of(new Leg(supplies, 30000L, 0L), new Leg(bankShinhan, 0L, 20000L))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("일치해야 합니다");

    verify(entryRepository, never()).save(any(EasyJournalEntry.class));
    verify(lineRepository, never()).save(any(EasyJournalLine.class));
  }

  @Test
  @DisplayName("8. 신규 SETTLEMENT 분개 — 거부")
  void settlementLeg_rejected() {
    when(entryRepository.save(any(EasyJournalEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> journalService.postLegs(academy,
        LocalDate.of(2026, 9, 10), null, "구방식",
        List.of(new Leg(supplies, 30000L, 0L), new Leg(settlement, 0L, 30000L))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SETTLEMENT");

    verify(entryRepository, never()).save(any(EasyJournalEntry.class));
  }

  @Test
  @DisplayName("9. 기존 SETTLEMENT 분개 경로 유지 — repost 동작 불변")
  void legacyRepost_stillReadable() {
    when(accountRepository.findByCode("SETTLEMENT")).thenReturn(Optional.of(settlement));
    when(entryRepository.save(any(EasyJournalEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(lineRepository.save(any(EasyJournalLine.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    EasyTransaction txn = EasyTransaction.builder()
        .business(academy).occurredOn(LocalDate.of(2025, 3, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .category("소모품").account(supplies).build();
    ReflectionTestUtils.setField(txn, "txnId", 99L);
    when(entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(
        99L, EasyJournalStatus.POSTED)).thenReturn(List.of());

    journalService.repost(txn);

    List<EasyJournalLine> lines = savedLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(1).getAccount().getCode()).isEqualTo("SETTLEMENT");
  }
}
