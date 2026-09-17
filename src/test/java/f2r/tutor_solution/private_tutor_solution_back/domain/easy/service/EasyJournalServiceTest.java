package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("EasyJournalService 복식엔진 테스트")
class EasyJournalServiceTest {

  @Mock EasyJournalEntryRepository entryRepository;
  @Mock EasyJournalLineRepository lineRepository;
  @Mock EasyAccountRepository accountRepository;

  private EasyJournalService service;
  private EasyBusiness academy;
  private EasyAccount tuition;
  private EasyAccount settlement;

  @BeforeEach
  void setUp() {
    service = new EasyJournalService(entryRepository, lineRepository, accountRepository);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(academy, "businessId", 1L);
    tuition = EasyAccount.builder().code("INCOME_TUITION").name("수강료")
        .direction(EasyAccountDirection.INCOME).sortOrder(1).active(true).build();
    settlement = EasyAccount.builder().code("SETTLEMENT").name("자금정산")
        .direction(EasyAccountDirection.ASSET).sortOrder(0).active(true).build();
  }

  private void stubPosting() {
    when(accountRepository.findByCode("SETTLEMENT")).thenReturn(Optional.of(settlement));
    when(entryRepository.save(any(EasyJournalEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(lineRepository.save(any(EasyJournalLine.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private EasyTransaction txn(Long txnId, EasyTxnDirection direction, long amount,
      EasyAccount account) {
    EasyTransaction txn = EasyTransaction.builder()
        .business(academy).occurredOn(LocalDate.of(2025, 3, 5)).direction(direction)
        .amount(amount).category("수강료").account(account).build();
    ReflectionTestUtils.setField(txn, "txnId", txnId);
    return txn;
  }

  @Test
  @DisplayName("수입 분개 — 차변 자금정산 / 대변 수강료, 차대변 일치")
  void repost_income_balanced() {
    stubPosting();
    when(entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(11L, EasyJournalStatus.POSTED))
        .thenReturn(List.of());

    service.repost(txn(11L, EasyTxnDirection.INCOME, 300000L, tuition));

    ArgumentCaptor<EasyJournalLine> lines = ArgumentCaptor.forClass(EasyJournalLine.class);
    verify(lineRepository, org.mockito.Mockito.times(2)).save(lines.capture());
    EasyJournalLine debit = lines.getAllValues().get(0);
    EasyJournalLine credit = lines.getAllValues().get(1);
    assertThat(debit.getAccount().getCode()).isEqualTo("SETTLEMENT");
    assertThat(debit.getDebit()).isEqualTo(300000L);
    assertThat(debit.getCredit()).isZero();
    assertThat(credit.getAccount().getCode()).isEqualTo("INCOME_TUITION");
    assertThat(credit.getCredit()).isEqualTo(300000L);
    assertThat(credit.getDebit()).isZero();
  }

  @Test
  @DisplayName("재분개 — 기존 활성 분개 void 후 1건만 활성")
  void repost_voidsPrevious() {
    stubPosting();
    EasyJournalEntry old = EasyJournalEntry.builder()
        .business(academy).occurredOn(LocalDate.of(2025, 3, 5)).build();
    when(entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(11L, EasyJournalStatus.POSTED))
        .thenReturn(List.of(old));

    service.repost(txn(11L, EasyTxnDirection.EXPENSE, 50000L, tuition));

    assertThat(old.getStatus()).isEqualTo(EasyJournalStatus.VOIDED);
    ArgumentCaptor<EasyJournalEntry> entries =
        ArgumentCaptor.forClass(EasyJournalEntry.class);
    verify(entryRepository).save(entries.capture());
    assertThat(entries.getValue().getReversedEntry()).isSameAs(old);
  }

  @Test
  @DisplayName("미분류·0원 거래는 분개하지 않음")
  void repost_skipsUnclassifiedAndZero() {
    when(entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(11L, EasyJournalStatus.POSTED))
        .thenReturn(List.of());
    when(entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(12L, EasyJournalStatus.POSTED))
        .thenReturn(List.of());

    service.repost(txn(11L, EasyTxnDirection.EXPENSE, 50000L, null));
    service.repost(txn(12L, EasyTxnDirection.INCOME, 0L, tuition));

    verify(entryRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  @DisplayName("시산표 — 차변 합계 == 대변 합계")
  void trialBalance_balanced() {
    EasyJournalEntry entry = EasyJournalEntry.builder()
        .business(academy).occurredOn(LocalDate.of(2025, 3, 5)).build();
    when(entryRepository.findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
        1L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), EasyJournalStatus.POSTED))
        .thenReturn(List.of(entry));
    when(lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(null))
        .thenReturn(List.of(
            EasyJournalLine.builder().entry(entry).account(tuition)
                .debit(50000L).credit(0L).sortOrder(0).build(),
            EasyJournalLine.builder().entry(entry).account(settlement)
                .debit(0L).credit(50000L).sortOrder(1).build()));

    var trial = service.trialBalance(1L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

    assertThat(trial.debitTotal()).isEqualTo(50000L);
    assertThat(trial.creditTotal()).isEqualTo(50000L);
    assertThat(trial.balanced()).isTrue();
  }
}
