package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAnnualClosing;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyReviewItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
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
@DisplayName("Easy Workspace 계산형 상태 조회")
class EasyWorkspaceServiceTest {

  @Mock EasyLedgerService ledgerService;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyTransactionRepository transactionRepository;
  @Mock EasyJournalEntryRepository journalEntryRepository;
  @Mock EasyAnnualClosingRepository closingRepository;
  @Mock EasyCollectionRawRecordRepository rawRecordRepository;
  @Mock EasyImportBatchRepository importBatchRepository;
  @Mock EasyReviewItemRepository reviewItemRepository;

  private EasyWorkspaceService service;
  private EasyBusiness academy;
  private EasyTransaction transaction;

  @BeforeEach
  void setUp() {
    academy = EasyBusiness.builder()
        .code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(academy, "businessId", 1L);
    transaction = EasyTransaction.builder()
        .business(academy)
        .occurredOn(LocalDate.of(2026, 10, 5))
        .direction(EasyTxnDirection.INCOME)
        .amount(300000L)
        .category("수강료")
        .sourceKind("CARD")
        .build();
    ReflectionTestUtils.setField(transaction, "txnId", 10L);
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    EasyBusinessScopeGuard guard = new EasyBusinessScopeGuard(businessRepository);
    service = new EasyWorkspaceService(
        guard, ledgerService, transactionRepository, journalEntryRepository,
        closingRepository, rawRecordRepository, importBatchRepository, reviewItemRepository);
  }

  @Test
  @DisplayName("거래 단건은 businessId 복합 조회로만 접근한다")
  void transaction_isBusinessScoped() {
    when(transactionRepository.findByTxnIdAndBusiness_BusinessId(10L, 1L))
        .thenReturn(Optional.of(transaction));
    when(journalEntryRepository.findByTxn_TxnIdOrderByEntryIdDesc(10L))
        .thenReturn(List.of());
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2026))
        .thenReturn(Optional.empty());

    var response = service.transaction(1L, 10L);

    assertThat(response.ledgerStatus()).isEqualTo("LEDGER_SAVED");
    assertThat(response.collectionStatus()).isEqualTo("COLLECTED");
    assertThat(response.journalStatus()).isEqualTo("NOT_POSTED");
    assertThat(response.closingStatus()).isEqualTo("OPEN");
    verify(transactionRepository).findByTxnIdAndBusiness_BusinessId(10L, 1L);
  }

  @Test
  @DisplayName("다른 사업체의 txnId는 존재 여부를 노출하지 않고 거부한다")
  void transaction_crossBusinessRejected() {
    when(transactionRepository.findByTxnIdAndBusiness_BusinessId(10L, 1L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.transaction(1L, 10L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("해당 사업체의 거래");
  }

  @Test
  @DisplayName("거래 수정도 원 사업체 복합 잠금 조회가 실패하면 차단한다")
  void updateTransaction_crossBusinessRejectedBeforeMutation() {
    when(transactionRepository.findLockedByTxnIdAndBusiness_BusinessId(10L, 1L))
        .thenReturn(Optional.empty());
    EasyTxnRequest request = new EasyTxnRequest(
        1L, LocalDate.of(2026, 10, 5), "INCOME", 300000L,
        "수강료", "학부모", "10월 수강료", "계좌이체", null, null);

    assertThatThrownBy(() -> service.updateTransaction(1L, 10L, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("해당 사업체의 거래");
  }

  @Test
  @DisplayName("월 대시보드는 금액과 네 단계 상태의 출처를 분리한다")
  void monthly_exposesSourcesAndComputedCounts() {
    LocalDate from = LocalDate.of(2026, 10, 1);
    LocalDate to = LocalDate.of(2026, 10, 31);
    when(ledgerService.summary(1L, YearMonth.of(2026, 10))).thenReturn(
        new EasyDashboardResponse("2026-10", 1L, "학원", 300000L, 100000L,
            200000L, 50000L, 2L, List.of(), List.of()));
    when(transactionRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            1L, from, to)).thenReturn(List.of(transaction));
    EasyJournalEntry entry = EasyJournalEntry.builder()
        .business(academy).occurredOn(transaction.getOccurredOn()).txn(transaction).build();
    when(journalEntryRepository.findByTxn_TxnIdOrderByEntryIdDesc(10L))
        .thenReturn(List.of(entry));
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2026))
        .thenReturn(Optional.of(EasyAnnualClosing.builder()
            .business(academy).fiscalYear(2026).build()));
    when(rawRecordRepository
        .findByBusiness_BusinessIdAndSourceOccurredOnBetweenOrderByRawIdDesc(1L, from, to))
        .thenReturn(List.of());
    when(importBatchRepository.countByBusiness_BusinessIdAndStatus(1L, EasyImportStatus.STAGED))
        .thenReturn(2L);
    when(reviewItemRepository.countByBusiness_BusinessIdAndStatus(1L, EasyReviewStatus.OPEN))
        .thenReturn(3L);
    when(reviewItemRepository.countByBusiness_BusinessIdAndKindAndStatus(
        1L, EasyReviewKind.EVIDENCE, EasyReviewStatus.OPEN)).thenReturn(1L);

    var response = service.monthly(1L, YearMonth.of(2026, 10));

    assertThat(response.ledgerSavedCount()).isEqualTo(1L);
    assertThat(response.journalPostedCount()).isEqualTo(1L);
    assertThat(response.importStagedCount()).isEqualTo(2L);
    assertThat(response.openReviewCount()).isEqualTo(3L);
    assertThat(response.sources().amounts()).contains("easy_transactions");
    assertThat(response.recentTransactions().get(0).journalStatus())
        .isEqualTo("JOURNAL_POSTED");
  }
}
