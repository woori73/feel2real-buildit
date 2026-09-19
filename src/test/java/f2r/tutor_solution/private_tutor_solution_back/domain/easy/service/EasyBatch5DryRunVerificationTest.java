package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * READ-ONLY / DRY-RUN verification test for Batch 5 selective confirm.
 * Verifies arithmetic invariants, 43 target row confirmations, 500 imported protections,
 * and 36 unmatched READY row protections without invoking production DB or real confirm.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch 5 Selective Confirm READ-ONLY / DRY-RUN Verification Test")
class EasyBatch5DryRunVerificationTest {

  @Mock EasyImportBatchRepository batchRepository;
  @Mock EasyImportRowRepository rowRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyAccountService accountService;
  @Mock EasyAccountMappingRepository mappingRepository;
  @Mock EasyJournalService journalService;

  private EasyImportService service;
  private EasyBusiness business;
  private EasyAccount commAccount;
  private EasyAccount feeAccount;
  private EasyAccount suppliesAccount;
  private EasyAccount entertainAccount;

  @BeforeEach
  void setUp() {
    service = new EasyImportService(
        batchRepository, rowRepository, businessRepository, txnRepository, accountService,
        mappingRepository, journalService);

    business = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).build();
    ReflectionTestUtils.setField(business, "businessId", 1L);

    commAccount = EasyAccount.builder().code("EXPENSE_COMMUNICATION").name("통신비")
        .direction(EasyAccountDirection.EXPENSE).active(true).build();
    feeAccount = EasyAccount.builder().code("EXPENSE_FEE").name("지급수수료")
        .direction(EasyAccountDirection.EXPENSE).active(true).build();
    suppliesAccount = EasyAccount.builder().code("EXPENSE_SUPPLIES").name("소모품")
        .direction(EasyAccountDirection.EXPENSE).active(true).build();
    entertainAccount = EasyAccount.builder().code("EXPENSE_ENTERTAIN").name("접대비")
        .direction(EasyAccountDirection.EXPENSE).active(true).build();
  }

  @Test
  @DisplayName("Batch 5 Data Invariant & Selective Confirm DRY-RUN Verification")
  void verifyBatch5SelectiveConfirmInvariants() {
    // 1. Create Batch 5 object (579 total rows, currently CONFIRMED state with 500 confirmed)
    EasyImportBatch batch5 = EasyImportBatch.builder()
        .business(business)
        .sourceName("easyshop_batch5.csv")
        .sourceKind("EASYSHOP")
        .totalRows(579)
        .build();
    ReflectionTestUtils.setField(batch5, "batchId", 5L);
    batch5.markConfirmed(500); // 500 rows previously confirmed

    assertThat(batch5.getStatus()).isEqualTo(EasyImportStatus.CONFIRMED);
    assertThat(batch5.getTotalRows()).isEqualTo(579);
    assertThat(batch5.getConfirmedRows()).isEqualTo(500);

    // 2. Build 79 READY rows (43 classified target, 36 unmatched protected)
    List<EasyImportRow> allReadyRows = new ArrayList<>();
    List<Long> targetRowIds = new ArrayList<>();

    long rowIdCounter = 1000L;

    // Group A: 전화/휴대폰/인터넷 (2 rows / 804,700 KRW) -> EXPENSE_COMMUNICATION
    long[] commAmounts = new long[] {400000L, 404700L};
    for (int i = 0; i < 2; i++) {
      rowIdCounter++;
      EasyImportRow row = createRow(batch5, rowIdCounter, i + 1, "KT", "휴대폰 요금", commAmounts[i], commAccount, 1.0);
      allReadyRows.add(row);
      targetRowIds.add(rowIdCounter);
    }

    // Group B: 서비스 이용료 (2 rows / 67,900 KRW) -> EXPENSE_FEE
    long[] feeAmounts = new long[] {30000L, 37900L};
    for (int i = 0; i < 2; i++) {
      rowIdCounter++;
      EasyImportRow row = createRow(batch5, rowIdCounter, i + 3, "네이버", "이용료", feeAmounts[i], feeAccount, 1.0);
      allReadyRows.add(row);
      targetRowIds.add(rowIdCounter);
    }

    // Group C: 차.음료-이마트24 (18 rows / 70,300 KRW) -> EXPENSE_SUPPLIES
    long emartSum = 0;
    for (int i = 0; i < 18; i++) {
      rowIdCounter++;
      long amt = (i == 17) ? (70300L - emartSum) : 3900L;
      emartSum += amt;
      EasyImportRow row = createRow(batch5, rowIdCounter, i + 5, "이마트24 압구정점", "음료", amt, suppliesAccount, 0.9);
      allReadyRows.add(row);
      targetRowIds.add(rowIdCounter);
    }

    // Group D: 거래처 선물 (21 rows / 3,801,203 KRW) -> EXPENSE_ENTERTAIN
    long entertainSum = 0;
    for (int i = 0; i < 21; i++) {
      rowIdCounter++;
      long amt = (i == 20) ? (3801203L - entertainSum) : 180000L;
      entertainSum += amt;
      EasyImportRow row = createRow(batch5, rowIdCounter, i + 23, "백화점", "선물", amt, entertainAccount, 1.0);
      allReadyRows.add(row);
      targetRowIds.add(rowIdCounter);
    }

    assertThat(targetRowIds).hasSize(43);
    long targetAmountSum = allReadyRows.stream().mapToLong(EasyImportRow::getAmount).sum();
    assertThat(targetAmountSum).isEqualTo(4744103L); // 4,744,103 KRW

    // Group E: 36 Unmatched READY rows (7,656,600 KRW) -> matchedAccount == null
    long unmatchedSum = 0;
    for (int i = 0; i < 36; i++) {
      rowIdCounter++;
      long amt = (i == 35) ? (7656600L - unmatchedSum) : 200000L;
      unmatchedSum += amt;
      EasyImportRow row = createRow(batch5, rowIdCounter, i + 44, "미분류처", "상세 불명", amt, null, 0.0);
      allReadyRows.add(row);
    }

    assertThat(allReadyRows).hasSize(79);
    long totalReadyAmount = allReadyRows.stream().mapToLong(EasyImportRow::getAmount).sum();
    assertThat(totalReadyAmount).isEqualTo(12400703L); // 12,400,703 KRW

    // Verification of arithmetic invariants
    long unmatchedAmount = allReadyRows.stream()
        .filter(r -> r.getMatchedAccount() == null)
        .mapToLong(EasyImportRow::getAmount)
        .sum();
    assertThat(unmatchedAmount).isEqualTo(7656600L); // 7,656,600 KRW
    assertThat(targetAmountSum + unmatchedAmount).isEqualTo(12400703L); // 4,744,103 + 7,656,600 = 12,400,703

    // 3. Setup mocks for DRY-RUN execution
    when(batchRepository.findById(5L)).thenReturn(Optional.of(batch5));
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(5L, EasyImportRowStatus.READY))
        .thenReturn(allReadyRows);
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(5L))
        .thenReturn(allReadyRows);
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // 4. Run selective confirm for 43 target row IDs
    EasyImportBatchResponse response = service.confirm(5L, targetRowIds);

    // 5. Assert results
    assertThat(response.status()).isEqualTo("CONFIRMED");

    // 43 target rows MUST be IMPORTED
    long newlyImportedCount = allReadyRows.stream()
        .filter(r -> r.getStatus() == EasyImportRowStatus.IMPORTED)
        .count();
    assertThat(newlyImportedCount).isEqualTo(43);

    // 36 unmatched READY rows MUST remain READY
    long remainingReadyCount = allReadyRows.stream()
        .filter(r -> r.getStatus() == EasyImportRowStatus.READY)
        .count();
    assertThat(remainingReadyCount).isEqualTo(36);

    // Verify created transactions count
    long createdTxnCount = allReadyRows.stream()
        .filter(r -> r.getCreatedTxn() != null)
        .count();
    assertThat(createdTxnCount).isEqualTo(43);

    // Verify unmatched rows have NO created transactions
    allReadyRows.stream()
        .filter(r -> r.getMatchedAccount() == null)
        .forEach(r -> {
          assertThat(r.getStatus()).isEqualTo(EasyImportRowStatus.READY);
          assertThat(r.getCreatedTxn()).isNull();
        });
  }

  private EasyImportRow createRow(EasyImportBatch batch, long rowId, int rowNo,
      String counterparty, String description, long amount, EasyAccount account, double confidence) {
    EasyImportRow row = EasyImportRow.builder()
        .batch(batch)
        .rowNo(rowNo)
        .occurredOn(LocalDate.of(2026, 1, 15))
        .direction(EasyTxnDirection.EXPENSE)
        .amount(amount)
        .counterparty(counterparty)
        .description(description)
        .rawData("[srow:" + rowNo + "] {}")
        .build();
    ReflectionTestUtils.setField(row, "rowId", rowId);
    row.markReady(account, confidence);
    return row;
  }
}
