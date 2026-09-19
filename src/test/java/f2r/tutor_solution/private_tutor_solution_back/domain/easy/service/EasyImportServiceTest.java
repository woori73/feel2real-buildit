package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAccountService.ClassificationMatch;
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
 * staging — 검증·확정·폐기. 실제 이지샵 데이터는 다루지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyImportService staging 테스트")
class EasyImportServiceTest {

  @Mock EasyImportBatchRepository batchRepository;
  @Mock EasyImportRowRepository rowRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyAccountService accountService;
  @Mock EasyAccountMappingRepository mappingRepository;
  @Mock EasyJournalService journalService;

  private EasyImportService service;
  private EasyBusiness academy;
  private EasyAccount telecom;

  @BeforeEach
  void setUp() {
    service = new EasyImportService(
        batchRepository, rowRepository, businessRepository, txnRepository, accountService,
        mappingRepository, journalService);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    telecom = EasyAccount.builder().code("EXPENSE_TELECOM").name("통신비")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(1).active(true).build();
  }

  private EasyImportRowInput input(String date, String direction, Long amount) {
    return new EasyImportRowInput(date, direction, amount, "KT", "요금",
        "계좌이체", null, null, null, "{\"raw\":1}");
  }

  @Test
  @DisplayName("정상 행 READY + 규칙 제안, 비정상 행 ERROR")
  void stage_validatesAndSuggests() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(batchRepository.save(any(EasyImportBatch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rowRepository.save(any(EasyImportRow.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(accountService.classify("KT", "요금", 30000L, EasyTxnDirection.EXPENSE))
        .thenReturn(Optional.of(new ClassificationMatch(telecom, 0.9)));

    EasyImportBatchResponse response = service.stage(new EasyImportBatchRequest(
        1L, "bank.csv", "CSV", List.of(
            input("2026-09-05", "EXPENSE", 30000L),
            input("잘못된날짜", "EXPENSE", 1000L),
            input("2026-09-06", "EXPENSE", -500L))));

    assertThat(response.rows()).hasSize(3);
    assertThat(response.rows().get(0).status()).isEqualTo("READY");
    assertThat(response.rows().get(0).matchedAccountName()).isEqualTo("통신비");
    assertThat(response.rows().get(1).status()).isEqualTo("ERROR");
    assertThat(response.rows().get(2).status()).isEqualTo("ERROR");
  }

  @Test
  @DisplayName("확정 — READY만 장부로, 배치 CONFIRMED. 재확정 시 READY 없으면 에러")
  void confirm_andDiscard() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("bank.csv").sourceKind("CSV").totalRows(1).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));
    EasyImportRow ready = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 9, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("KT").description("요금").build();
    ready.markReady(telecom, 0.9);
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(ready), List.of());
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L)).thenReturn(List.of(ready));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(1L);

    EasyImportBatchResponse confirmed = service.confirm(9L, null);

    assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    assertThat(confirmed.confirmedRows()).isEqualTo(1);
    assertThat(ready.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(ready.getCreatedTxn()).isNotNull();
    assertThat(ready.getCreatedTxn().getCategory()).isEqualTo("통신비");

    // 재확정 시 READY가 없으므로 에러 (이미 IMPORTED된 row는 다시 확정되지 않음)
    assertThatThrownBy(() -> service.confirm(9L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("확정할 READY 행이 없습니다");
  }

  @Test
  @DisplayName("CONFIRMED batch의 READY 행 선택 확정 — partial confirm")
  void confirm_conFIRMEDBatchWithReadyRows() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(3).build();
    batch.markConfirmed(1);
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyAccount fee = EasyAccount.builder().code("EXPENSE_FEE").name("지급수수료")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(17).active(true).build();

    EasyImportRow row1 = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    row1.markReady(telecom, 1.0); // 이미 재분류 완료됨 (matchedAccount 설정됨)
    ReflectionTestUtils.setField(row1, "rowId", 100L);

    EasyImportRow row2 = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("네이버").description("서비스 이용료").externalAccount("서비스 이용료").build();
    row2.markReady(fee, 1.0); // 이미 재분류 완료됨
    ReflectionTestUtils.setField(row2, "rowId", 101L);

    EasyImportRow row3 = EasyImportRow.builder()
        .batch(batch).rowNo(3)
        .occurredOn(java.time.LocalDate.of(2026, 1, 7))
        .direction(EasyTxnDirection.EXPENSE).amount(100000L)
        .counterparty("거래처").description("선물").externalAccount("거래처 선물").build();
    row3.markReady(null, 0.0); // 미분류 - 선택하지 않음
    ReflectionTestUtils.setField(row3, "rowId", 102L);

    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(row1, row2, row3));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    // 기존 1건 + 신규 2건 = 누적 3건 (row-level IMPORTED 실측)
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(3L);

    // CONFIRMED batch에서 2건만 선택 확정
    EasyImportBatchResponse confirmed = service.confirm(9L, List.of(100L, 101L));

    assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    assertThat(confirmed.confirmedRows()).isEqualTo(3);
    assertThat(row1.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(row1.getCreatedTxn()).isNotNull();
    assertThat(row1.getCreatedTxn().getAccount()).isEqualTo(telecom);
    assertThat(row2.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(row2.getCreatedTxn()).isNotNull();
    assertThat(row2.getCreatedTxn().getAccount()).isEqualTo(fee);
    // row3은 선택하지 않았으므로 READY 유지
    assertThat(row3.getStatus()).isEqualTo(EasyImportRowStatus.READY);
    assertThat(row3.getCreatedTxn()).isNull();
  }

  @Test
  @DisplayName("CONFIRMED batch에서 unmatched READY 행 보호 — matchedAccount=null은 확정 안 됨")
  void confirm_conFIRMEDBatchProtectsUnmatchedReady() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(2).build();
    batch.markConfirmed(1);
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyImportRow matched = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    matched.markReady(telecom, 1.0); // matchedAccount 설정됨
    ReflectionTestUtils.setField(matched, "rowId", 100L);

    EasyImportRow unmatched = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("알수없음").description("분류불가").externalAccount("분류불가").build();
    unmatched.markReady(null, 0.0); // matchedAccount = null (미분류)
    ReflectionTestUtils.setField(unmatched, "rowId", 101L);

    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(matched, unmatched));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    // 기존 1건 + 신규 1건 = 누적 2건 (unmatched는 실측에 포함되지 않음)
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(2L);

    // 둘 다 rowIds에 포함시켰지만 matchedAccount 없는 unmatched는 확정되지 않음
    EasyImportBatchResponse confirmed = service.confirm(9L, List.of(100L, 101L));

    assertThat(confirmed.confirmedRows()).isEqualTo(2);
    assertThat(matched.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(unmatched.getStatus()).isEqualTo(EasyImportRowStatus.READY);
    assertThat(unmatched.getCreatedTxn()).isNull();
  }

  @Test
  @DisplayName("이미 IMPORTED된 row는 rowIds에 포함돼도 중복 생성 안 됨 (idempotency)")
  void confirm_idempotentOnAlreadyImported() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(2).build();
    batch.markConfirmed(1);
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyAccount fee = EasyAccount.builder().code("EXPENSE_FEE").name("지급수수료")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(17).active(true).build();

    EasyImportRow alreadyImported = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    alreadyImported.markReady(telecom, 1.0);
    ReflectionTestUtils.setField(alreadyImported, "rowId", 100L);
    EasyTransaction txn = EasyTransaction.builder()
        .business(academy).occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .category("통신비").counterparty("KT").description("전화요금").build();
    ReflectionTestUtils.setField(txn, "txnId", 1L);
    alreadyImported.markImported(txn);
    ReflectionTestUtils.setField(alreadyImported, "rowId", 100L);

    EasyImportRow ready = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("네이버").description("서비스 이용료").externalAccount("서비스 이용료").build();
    ready.markReady(fee, 1.0); // matchedAccount 설정됨
    ReflectionTestUtils.setField(ready, "rowId", 101L);

    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(ready)); // 이미 IMPORTED된 row는 READY 조회에 포함되지 않음
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    // 기존 1건 + 신규 1건 = 누적 2건 (중복 생성 없으므로 증가분은 신규 1건뿐)
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(2L);

    // 이미 IMPORTED된 rowId 포함해도 중복 생성 안 됨
    EasyImportBatchResponse confirmed = service.confirm(9L, List.of(100L, 101L));

    assertThat(confirmed.confirmedRows()).isEqualTo(2);
    assertThat(alreadyImported.getCreatedTxn()).isSameAs(txn); // 기존 txn 유지
    assertThat(ready.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    verify(txnRepository, times(1)).save(any(EasyTransaction.class)); // 신규 1건만 저장
  }

  @Test
  @DisplayName("CONFIRMED batch 누적 확정 — 기존 500 + 신규 43 = 543 (Batch 5 기대값)")
  void confirm_accumulatesCountOnConfirmedBatch() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(579).build();
    batch.markConfirmed(500);
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    java.util.List<EasyImportRow> targets = new java.util.ArrayList<>();
    for (int i = 0; i < 43; i++) {
      EasyImportRow row = EasyImportRow.builder()
          .batch(batch).rowNo(i + 1)
          .occurredOn(java.time.LocalDate.of(2026, 1, 5))
          .direction(EasyTxnDirection.EXPENSE).amount(10000L)
          .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
      row.markReady(telecom, 1.0);
      ReflectionTestUtils.setField(row, "rowId", 1000L + i);
      targets.add(row);
    }

    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(targets);
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    // row-level IMPORTED 실측: 기존 500 + 신규 43
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(543L);

    EasyImportBatchResponse confirmed = service.confirm(9L, null);

    assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    assertThat(confirmed.confirmedRows()).isEqualTo(543);
    assertThat(targets).allMatch(row -> row.getStatus() == EasyImportRowStatus.IMPORTED);
    assertThat(targets).allMatch(row -> row.getCreatedTxn() != null);
    verify(txnRepository, times(43)).save(any(EasyTransaction.class));
    verify(rowRepository).countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED);
  }

  @Test
  @DisplayName("CONFIRMED batch에서 선택 대상 전부 unmatched — 기존 500 유지, 저장 없음")
  void confirm_allUnmatchedKeepsCount() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(502).build();
    batch.markConfirmed(500);
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyImportRow unmatched1 = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("알수없음").description("분류불가").externalAccount("분류불가").build();
    unmatched1.markReady(null, 0.0);
    ReflectionTestUtils.setField(unmatched1, "rowId", 100L);

    EasyImportRow unmatched2 = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(40000L)
        .counterparty("모름").description("내역없음").externalAccount("내역없음").build();
    unmatched2.markReady(null, 0.0);
    ReflectionTestUtils.setField(unmatched2, "rowId", 101L);

    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(unmatched1, unmatched2));
    // 신규 확정 0건이므로 실측은 기존 500 그대로
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(500L);

    EasyImportBatchResponse confirmed = service.confirm(9L, List.of(100L, 101L));

    assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    assertThat(confirmed.confirmedRows()).isEqualTo(500);
    assertThat(unmatched1.getStatus()).isEqualTo(EasyImportRowStatus.READY);
    assertThat(unmatched2.getStatus()).isEqualTo(EasyImportRowStatus.READY);
    verify(txnRepository, never()).save(any(EasyTransaction.class));
  }

  @Test
  @DisplayName("READY 조회에 섞인 createdTxn 보유 행은 건너뜀 — 중복 transaction 없음")
  void confirm_skipsReadyRowWithExistingTxn() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(501).build();
    batch.markConfirmed(500);
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyTransaction existing = EasyTransaction.builder()
        .business(academy).occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .category("통신비").counterparty("KT").description("전화요금").build();
    ReflectionTestUtils.setField(existing, "txnId", 7L);

    // 상태 불일치 시뮬레이션: READY이지만 createdTxn 보유
    EasyImportRow inconsistent = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    inconsistent.markReady(telecom, 1.0);
    ReflectionTestUtils.setField(inconsistent, "rowId", 100L);
    // READY 유지 + createdTxn 보유라는 불일치 상태 (DB에는 없어야 하나 방어 차원 검증)
    ReflectionTestUtils.setField(inconsistent, "createdTxn", existing);

    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(inconsistent));
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(500L);

    EasyImportBatchResponse confirmed = service.confirm(9L, List.of(100L));

    assertThat(confirmed.confirmedRows()).isEqualTo(500);
    assertThat(inconsistent.getStatus()).isEqualTo(EasyImportRowStatus.READY);
    assertThat(inconsistent.getCreatedTxn()).isSameAs(existing);
    verify(txnRepository, never()).save(any(EasyTransaction.class));
  }

  @Test
  @DisplayName("일부 matched + 일부 unmatched — 실제 새 IMPORTED 수만 누적 (500 → 502)")
  void confirm_mixedRowsAccumulateOnlyNew() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(503).build();
    batch.markConfirmed(500);
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyImportRow matched1 = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    matched1.markReady(telecom, 1.0);
    ReflectionTestUtils.setField(matched1, "rowId", 100L);

    EasyImportRow matched2 = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("네이버").description("서비스 이용료").externalAccount("서비스 이용료").build();
    matched2.markReady(telecom, 1.0);
    ReflectionTestUtils.setField(matched2, "rowId", 101L);

    EasyImportRow unmatched = EasyImportRow.builder()
        .batch(batch).rowNo(3)
        .occurredOn(java.time.LocalDate.of(2026, 1, 7))
        .direction(EasyTxnDirection.EXPENSE).amount(100000L)
        .counterparty("알수없음").description("분류불가").externalAccount("분류불가").build();
    unmatched.markReady(null, 0.0);
    ReflectionTestUtils.setField(unmatched, "rowId", 102L);

    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(matched1, matched2, unmatched));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    // 실측: 기존 500 + 신규 2
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(502L);

    EasyImportBatchResponse confirmed = service.confirm(9L, List.of(100L, 101L, 102L));

    assertThat(confirmed.confirmedRows()).isEqualTo(502);
    assertThat(matched1.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(matched2.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(unmatched.getStatus()).isEqualTo(EasyImportRowStatus.READY);
    verify(txnRepository, times(2)).save(any(EasyTransaction.class));
  }

  @Test
  @DisplayName("CONFIRMED batch라도 rowIds가 빈 리스트이면 에러")
  void confirm_rejectsEmptyRowIds() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(1).build();
    batch.markConfirmed(1);
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    assertThatThrownBy(() -> service.confirm(9L, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("확정할 READY 행이 없습니다");
  }

  @Test
  @DisplayName("DISCARDED/ERROR 상태 batch는 여전히 거부")
  void confirm_rejectsInvalidStatus() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(1).build();
    batch.markDiscarded();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    assertThatThrownBy(() -> service.confirm(9L, List.of(100L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("STAGED 또는 CONFIRMED 상태의 배치만");
  }

  @Test
  @DisplayName("빈 행·2000행 초과 거부")
  void stage_rejectsEmptyAndOversize() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));

    assertThatThrownBy(() -> service.stage(
        new EasyImportBatchRequest(1L, "x.csv", "CSV", List.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("외부 계정명 확정 매핑 우선 — 규칙보다 먼저, 확신도 1.0")
  void stage_mappingFirst() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(batchRepository.save(any(EasyImportBatch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rowRepository.save(any(EasyImportRow.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping mapping =
        f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping
            .builder()
            .account(telecom).externalCode("통신비").externalName("KT 통신요금")
            .standardCode(null).standardName(null).note(null).build();
    when(mappingRepository.findFirstByExternalCode("통신비"))
        .thenReturn(Optional.of(mapping));

    EasyImportBatchResponse response = service.stage(new EasyImportBatchRequest(
        1L, "easyshop.csv", "EASYSHOP", List.of(
            new EasyImportRowInput("2025-03-05", "EXPENSE", 30000L, "KT", "요금",
                "계좌이체", null, null, null, null, "통신비"))));

    assertThat(response.rows().get(0).status()).isEqualTo("READY");
    assertThat(response.rows().get(0).matchedAccountName()).isEqualTo("통신비");
    assertThat(response.rows().get(0).confidence()).isEqualTo(1.0);
    assertThat(response.rows().get(0).externalAccount()).isEqualTo("통신비");
    verify(accountService, never()).classify(any(), any(), any(Long.class), any());
  }

  @Test
  @DisplayName("sourceRowNo 마커 있으면 #orig-row-N (B)")
  void confirm_usesOrigRowWhenMarked() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(1).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));
    EasyImportRow ready = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2025, 3, 5))
        .direction(EasyTxnDirection.INCOME).amount(300000L)
        .counterparty("카드사").description("카드(1)")
        .rawData("[srow:2] {\"date\":\"2025-03-05\"}").build();
    ready.markReady(telecom, 0.9);
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(ready));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L)).thenReturn(List.of(ready));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(1L);

    service.confirm(9L, null);

    assertThat(ready.getCreatedTxn().getSourceRef()).isEqualTo("easyshop.csv#orig-row-2");
  }

  @Test
  @DisplayName("payload 일부만 전송해도 원본 번호 유지 (C)")
  void confirm_keepsOrigRowForPartialPayload() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(2).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));
    EasyImportRow first = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2025, 3, 5))
        .direction(EasyTxnDirection.INCOME).amount(300000L)
        .rawData("[srow:5] {}").build();
    first.markReady(telecom, 0.9);
    EasyImportRow second = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2025, 3, 6))
        .direction(EasyTxnDirection.INCOME).amount(310000L)
        .rawData("[srow:6] {}").build();
    second.markReady(telecom, 0.9);
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(first, second));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L))
        .thenReturn(List.of(first, second));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(2L);

    service.confirm(9L, null);

    assertThat(first.getCreatedTxn().getSourceRef()).isEqualTo("easyshop.csv#orig-row-5");
    assertThat(second.getCreatedTxn().getSourceRef()).isEqualTo("easyshop.csv#orig-row-6");
  }

  @Test
  @DisplayName("확정 시 출처 추적 + 분개 위임")
  void confirm_postsJournalWithProvenance() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(1).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));
    EasyImportRow ready = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2025, 3, 5))
        .direction(EasyTxnDirection.INCOME).amount(300000L)
        .counterparty("학부모").description("수강료").build();
    ready.markReady(telecom, 0.9);
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(ready));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L)).thenReturn(List.of(ready));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(1L);

    service.confirm(9L, null);

    EasyTransaction created = ready.getCreatedTxn();
    assertThat(created.getSourceKind()).isEqualTo("EASYSHOP");
    assertThat(created.getSourceRef()).isEqualTo("easyshop.csv#row-2");
    assertThat(created.getFingerprint()).isNotNull();
    verify(journalService).repost(created);
  }

  @Test
  @DisplayName("reclassify — 명시된 READY rowId만 매핑 재계산, 미지정 row 불변")
  void reclassify_targetedRowsOnly() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(3).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyAccount fee = EasyAccount.builder().code("EXPENSE_FEE").name("지급수수료")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(17).active(true).build();
    EasyAccount entertain = EasyAccount.builder().code("EXPENSE_ENTERTAIN").name("접대비")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(20).active(true).build();

    EasyImportRow row1 = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    row1.markReady(null, 0.0);
    ReflectionTestUtils.setField(row1, "rowId", 100L);

    EasyImportRow row2 = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("네이버").description("서비스 이용료").externalAccount("서비스 이용료").build();
    row2.markReady(null, 0.0);
    ReflectionTestUtils.setField(row2, "rowId", 101L);

    EasyImportRow row3 = EasyImportRow.builder()
        .batch(batch).rowNo(3)
        .occurredOn(java.time.LocalDate.of(2026, 1, 7))
        .direction(EasyTxnDirection.EXPENSE).amount(100000L)
        .counterparty("거래처").description("선물").externalAccount("거래처 선물").build();
    row3.markReady(null, 0.0);
    ReflectionTestUtils.setField(row3, "rowId", 102L);

    when(rowRepository.findByBatch_BatchIdAndRowIdIn(9L, List.of(100L, 101L)))
        .thenReturn(List.of(row1, row2));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L))
        .thenReturn(List.of(row1, row2, row3));
    when(mappingRepository.findFirstByExternalCode("전화/휴대폰/인터넷"))
        .thenReturn(Optional.of(EasyAccountMapping.builder().account(telecom).externalCode("전화/휴대폰/인터넷").build()));
    when(mappingRepository.findFirstByExternalCode("서비스 이용료"))
        .thenReturn(Optional.of(EasyAccountMapping.builder().account(fee).externalCode("서비스 이용료").build()));

    service.reclassify(9L, List.of(100L, 101L));

    assertThat(row1.getMatchedAccount()).isEqualTo(telecom);
    assertThat(row1.getConfidence()).isEqualTo(1.0);
    assertThat(row2.getMatchedAccount()).isEqualTo(fee);
    assertThat(row2.getConfidence()).isEqualTo(1.0);
    assertThat(row3.getMatchedAccount()).isNull();
    assertThat(row3.getConfidence()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("reclassify — CONFIRMED/IMPORTED row는 건너뜀 (createdTxn 있으면 무시)")
  void reclassify_skipsConfirmedRows() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(1).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyImportRow row = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    row.markReady(null, 0.0);
    ReflectionTestUtils.setField(row, "rowId", 100L);
    EasyTransaction txn = EasyTransaction.builder()
        .business(academy).occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .category("기타").counterparty("KT").description("전화요금").build();
    ReflectionTestUtils.setField(txn, "txnId", 1L);
    row.markImported(txn);

    when(rowRepository.findByBatch_BatchIdAndRowIdIn(9L, List.of(100L)))
        .thenReturn(List.of(row));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L))
        .thenReturn(List.of(row));

    service.reclassify(9L, List.of(100L));

    assertThat(row.getMatchedAccount()).isNull();
    assertThat(row.getCreatedTxn()).isSameAs(txn);
  }

  @Test
  @DisplayName("reclassify — DUPLICATE/ERROR row는 건너뜀")
  void reclassify_skipsNonReadyRows() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(2).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyImportRow duplicate = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").build();
    duplicate.markDuplicate("장부 중복");
    ReflectionTestUtils.setField(duplicate, "rowId", 100L);

    EasyImportRow error = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("네이버").description("서비스").build();
    error.markError("날짜 오류");
    ReflectionTestUtils.setField(error, "rowId", 101L);

    when(rowRepository.findByBatch_BatchIdAndRowIdIn(9L, List.of(100L, 101L)))
        .thenReturn(List.of(duplicate, error));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L))
        .thenReturn(List.of(duplicate, error));

    service.reclassify(9L, List.of(100L, 101L));

    assertThat(duplicate.getStatus()).isEqualTo(EasyImportRowStatus.DUPLICATE);
    assertThat(error.getStatus()).isEqualTo(EasyImportRowStatus.ERROR);
  }

  @Test
  @DisplayName("reclassify — 다른 batch의 rowId는 거부")
  void reclassify_rejectsOtherBatchRows() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(1).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    when(rowRepository.findByBatch_BatchIdAndRowIdIn(9L, List.of(999L)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.reclassify(9L, List.of(999L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이 배치에 속하지 않거나 존재하지 않습니다");
  }

  @Test
  @DisplayName("reclassify — 빈 rowIds 거부")
  void reclassify_rejectsEmptyRowIds() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(1).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    assertThatThrownBy(() -> service.reclassify(9L, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("재분류할 rowId를 지정해 주세요");
  }

  @Test
  @DisplayName("reclassify 후 partial confirm 정상 동작")
  void reclassifyThenPartialConfirm() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(2).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyAccount fee = EasyAccount.builder().code("EXPENSE_FEE").name("지급수수료")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(17).active(true).build();

    EasyImportRow row1 = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    row1.markReady(null, 0.0);
    ReflectionTestUtils.setField(row1, "rowId", 100L);

    EasyImportRow row2 = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("네이버").description("서비스 이용료").externalAccount("서비스 이용료").build();
    row2.markReady(null, 0.0);
    ReflectionTestUtils.setField(row2, "rowId", 101L);

    when(rowRepository.findByBatch_BatchIdAndRowIdIn(9L, List.of(100L)))
        .thenReturn(List.of(row1));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L))
        .thenReturn(List.of(row1, row2));
    when(mappingRepository.findFirstByExternalCode("전화/휴대폰/인터넷"))
        .thenReturn(Optional.of(EasyAccountMapping.builder().account(telecom).externalCode("전화/휴대폰/인터넷").build()));

    service.reclassify(9L, List.of(100L));

    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(9L, EasyImportRowStatus.READY))
        .thenReturn(List.of(row1));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rowRepository.countByBatch_BatchIdAndStatus(9L, EasyImportRowStatus.IMPORTED))
        .thenReturn(1L);

    EasyImportBatchResponse confirmed = service.confirm(9L, List.of(100L));

    assertThat(confirmed.confirmedRows()).isEqualTo(1);
    assertThat(row1.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(row1.getCreatedTxn().getAccount()).isEqualTo(telecom);
    assertThat(row2.getStatus()).isEqualTo(EasyImportRowStatus.READY);
  }

  @Test
  @DisplayName("reclassify — 이마트24 READY row → EXPENSE_SUPPLIES (classify fallback)")
  void reclassify_emart24RowToSupplies() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(3).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyAccount supplies = EasyAccount.builder().code("EXPENSE_SUPPLIES").name("소모품")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(16).active(true).build();
    EasyAccount entertain = EasyAccount.builder().code("EXPENSE_ENTERTAIN").name("접대비")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(20).active(true).build();

    EasyImportRow row1 = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(5000L)
        .counterparty("이마트24 압구정점").description("차.음료").externalAccount("차.음료").build();
    row1.markReady(null, 0.0);
    ReflectionTestUtils.setField(row1, "rowId", 100L);

    EasyImportRow row2 = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(30000L)
        .counterparty("거래처").description("선물").externalAccount("거래처 선물").build();
    row2.markReady(null, 0.0);
    ReflectionTestUtils.setField(row2, "rowId", 101L);

    EasyImportRow row3 = EasyImportRow.builder()
        .batch(batch).rowNo(3)
        .occurredOn(java.time.LocalDate.of(2026, 1, 7))
        .direction(EasyTxnDirection.EXPENSE).amount(5000L)
        .counterparty("스타벅스").description("차.음료").externalAccount("차.음료").build();
    row3.markReady(null, 0.0);
    ReflectionTestUtils.setField(row3, "rowId", 102L);

    when(rowRepository.findByBatch_BatchIdAndRowIdIn(9L, List.of(100L, 101L, 102L)))
        .thenReturn(List.of(row1, row2, row3));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L))
        .thenReturn(List.of(row1, row2, row3));
    when(mappingRepository.findFirstByExternalCode("차.음료"))
        .thenReturn(Optional.empty());
    when(mappingRepository.findFirstByExternalCode("거래처 선물"))
        .thenReturn(Optional.of(EasyAccountMapping.builder().account(entertain).externalCode("거래처 선물").build()));
    when(accountService.classify("이마트24 압구정점", "차.음료", 5000L, EasyTxnDirection.EXPENSE))
        .thenReturn(Optional.of(new ClassificationMatch(supplies, 0.9)));
    when(accountService.classify("스타벅스", "차.음료", 5000L, EasyTxnDirection.EXPENSE))
        .thenReturn(Optional.empty());

    service.reclassify(9L, List.of(100L, 101L, 102L));

    assertThat(row1.getMatchedAccount()).isEqualTo(supplies);
    assertThat(row1.getConfidence()).isEqualTo(0.9);
    assertThat(row2.getMatchedAccount()).isEqualTo(entertain);
    assertThat(row2.getConfidence()).isEqualTo(1.0);
    assertThat(row3.getMatchedAccount()).isNull();
    assertThat(row3.getConfidence()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("reclassify — V979 3개 Golden mapping + V980 이마트24 rule이 모두 적용")
  void reclassify_all43CategoriesMapped() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("easyshop.csv").sourceKind("EASYSHOP").totalRows(4).build();
    when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));

    EasyAccount comm = EasyAccount.builder().code("EXPENSE_COMMUNICATION").name("통신비")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(23).active(true).build();
    EasyAccount fee = EasyAccount.builder().code("EXPENSE_FEE").name("지급수수료")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(17).active(true).build();
    EasyAccount supplies = EasyAccount.builder().code("EXPENSE_SUPPLIES").name("소모품")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(16).active(true).build();
    EasyAccount entertain = EasyAccount.builder().code("EXPENSE_ENTERTAIN").name("접대비")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(20).active(true).build();

    EasyImportRow row1 = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(java.time.LocalDate.of(2026, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(10000L)
        .counterparty("KT").description("전화요금").externalAccount("전화/휴대폰/인터넷").build();
    row1.markReady(null, 0.0);
    ReflectionTestUtils.setField(row1, "rowId", 100L);

    EasyImportRow row2 = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(java.time.LocalDate.of(2026, 1, 6))
        .direction(EasyTxnDirection.EXPENSE).amount(5000L)
        .counterparty("네이버").description("서비스 이용료").externalAccount("서비스 이용료").build();
    row2.markReady(null, 0.0);
    ReflectionTestUtils.setField(row2, "rowId", 101L);

    EasyImportRow row3 = EasyImportRow.builder()
        .batch(batch).rowNo(3)
        .occurredOn(java.time.LocalDate.of(2026, 1, 7))
        .direction(EasyTxnDirection.EXPENSE).amount(5000L)
        .counterparty("이마트24").description("차.음료").externalAccount("차.음료").build();
    row3.markReady(null, 0.0);
    ReflectionTestUtils.setField(row3, "rowId", 102L);

    EasyImportRow row4 = EasyImportRow.builder()
        .batch(batch).rowNo(4)
        .occurredOn(java.time.LocalDate.of(2026, 1, 8))
        .direction(EasyTxnDirection.EXPENSE).amount(10000L)
        .counterparty("거래처").description("선물").externalAccount("거래처 선물").build();
    row4.markReady(null, 0.0);
    ReflectionTestUtils.setField(row4, "rowId", 103L);

    when(rowRepository.findByBatch_BatchIdAndRowIdIn(9L, List.of(100L, 101L, 102L, 103L)))
        .thenReturn(List.of(row1, row2, row3, row4));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(9L))
        .thenReturn(List.of(row1, row2, row3, row4));
    when(mappingRepository.findFirstByExternalCode("전화/휴대폰/인터넷"))
        .thenReturn(Optional.of(EasyAccountMapping.builder().account(comm).externalCode("전화/휴대폰/인터넷").build()));
    when(mappingRepository.findFirstByExternalCode("서비스 이용료"))
        .thenReturn(Optional.of(EasyAccountMapping.builder().account(fee).externalCode("서비스 이용료").build()));
    when(mappingRepository.findFirstByExternalCode("차.음료"))
        .thenReturn(Optional.empty());
    when(mappingRepository.findFirstByExternalCode("거래처 선물"))
        .thenReturn(Optional.of(EasyAccountMapping.builder().account(entertain).externalCode("거래처 선물").build()));
    when(accountService.classify("이마트24", "차.음료", 5000L, EasyTxnDirection.EXPENSE))
        .thenReturn(Optional.of(new ClassificationMatch(supplies, 0.9)));

    service.reclassify(9L, List.of(100L, 101L, 102L, 103L));

    assertThat(row1.getMatchedAccount()).isEqualTo(comm);
    assertThat(row1.getConfidence()).isEqualTo(1.0);
    assertThat(row2.getMatchedAccount()).isEqualTo(fee);
    assertThat(row2.getConfidence()).isEqualTo(1.0);
    assertThat(row3.getMatchedAccount()).isEqualTo(supplies);
    assertThat(row3.getConfidence()).isEqualTo(0.9);
    assertThat(row4.getMatchedAccount()).isEqualTo(entertain);
    assertThat(row4.getConfidence()).isEqualTo(1.0);
  }
}
