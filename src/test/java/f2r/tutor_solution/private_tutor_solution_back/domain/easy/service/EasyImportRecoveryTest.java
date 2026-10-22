package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceReview;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTaxClassification;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
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

/**
 * EASY 운영 원칙 복구 테스트 — 수집자료 HOLD 금지.
 * given/when/then: 실제 거래는 분류 확정과 무관하게 장부에 적재되고,
 * 불확실한 분류만 REVIEW 표시된다. 대출·계좌이체는 손익에 들어가지 않고,
 * 출판사 확정분은 출판사 장부에 귀속된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyImport 복구(장부 적재 우선) 테스트")
class EasyImportRecoveryTest {

  @Mock EasyImportBatchRepository batchRepository;
  @Mock EasyImportRowRepository rowRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyAccountService accountService;
  @Mock EasyAccountMappingRepository mappingRepository;
  @Mock EasyJournalService journalService;

  private EasyImportService service;
  private EasyBusiness academy;
  private EasyBusiness publishing;
  private EasyAccount rent;

  @BeforeEach
  void setUp() {
    service = new EasyImportService(
        batchRepository, rowRepository, businessRepository, txnRepository, accountService,
        mappingRepository, journalService,
        org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository.class),
        org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionApprovalRepository.class),
        org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService.class));
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(academy, "businessId", 1L);
    publishing = EasyBusiness.builder().code("PUBLISHING").name("출판사").active(true).sortOrder(2).build();
    ReflectionTestUtils.setField(publishing, "businessId", 2L);
    rent = EasyAccount.builder().code("EXPENSE_RENT").name("임차료")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(12).active(true).build();
  }

  private EasyImportBatch stagedBatch() {
    EasyImportBatch batch = EasyImportBatch.builder()
        .business(academy).sourceName("bank_import_20260927").sourceKind("BANK_CSV").totalRows(1).build();
    ReflectionTestUtils.setField(batch, "batchId", 1L);
    when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
    return batch;
  }

  private EasyImportRow pendingRow(EasyImportBatch batch, int rowNo, LocalDate date,
      EasyTxnDirection direction, Long amount, String rawData) {
    return EasyImportRow.builder()
        .batch(batch).rowNo(rowNo)
        .occurredOn(date).direction(direction).amount(amount)
        .counterparty("거래처").description("적요").payMethod("15:21:07")
        .rawData(rawData).build();
  }

  private void stubTargets(Long batchId, List<EasyImportRow> pending, List<EasyImportRow> ready,
      long importedCount) {
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
        batchId, EasyImportRowStatus.PENDING)).thenReturn(pending);
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
        batchId, EasyImportRowStatus.READY)).thenReturn(ready);
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(batchId))
        .thenReturn(ready.isEmpty() ? pending : ready);
    when(rowRepository.countByBatch_BatchIdAndStatus(batchId, EasyImportRowStatus.IMPORTED))
        .thenReturn(importedCount);
    // 저장이 없는 케이스(DUPLICATE/ERROR)에서도 엄격 스텁 검사를 피한다
    lenient().when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("PENDING 행은 confirm 시 자동 준비되어 장부에 적재된다")
  void confirm_autoPreparesPendingRows() {
    // given: 직접 적재된 PENDING 은행 행 (매핑·규칙 없음 → 미분류)
    EasyImportBatch batch = stagedBatch();
    EasyImportRow row = pendingRow(batch, 1, LocalDate.of(2026, 9, 23),
        EasyTxnDirection.EXPENSE, 38025L, "{\"bank\": \"KB\"}");
    stubTargets(1L, List.of(row), List.of(row), 1L);

    // when
    EasyImportBatchResponse confirmed = service.confirm(1L, null);

    // then: REVIEW로 장부에 적재 (HOLD 없음)
    assertThat(confirmed.confirmedRows()).isEqualTo(1);
    assertThat(row.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    EasyTransaction txn = row.getCreatedTxn();
    assertThat(txn).isNotNull();
    assertThat(txn.getBusiness()).isSameAs(academy);
    assertThat(txn.getAccount()).isNull();
    assertThat(txn.getCategory()).isEqualTo("미분류");
    assertThat(txn.getEvidenceReview()).isEqualTo(EasyEvidenceReview.NEEDS_REVIEW);
    assertThat(txn.getTaxClassification()).isEqualTo(EasyTaxClassification.REVIEW_REQUIRED);
    verify(journalService).repost(txn);
  }

  @Test
  @DisplayName("prepare()는 장부 적재 없이 PENDING을 READY로만 올린다")
  void prepare_onlyReadiesWithoutPosting() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow row = pendingRow(batch, 1, LocalDate.of(2026, 9, 23),
        EasyTxnDirection.EXPENSE, 38025L, null);
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
        1L, EasyImportRowStatus.PENDING)).thenReturn(List.of(row));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(1L)).thenReturn(List.of(row));

    // when
    EasyImportBatchResponse prepared = service.prepare(1L, null);

    // then
    assertThat(row.getStatus()).isEqualTo(EasyImportRowStatus.READY);
    assertThat(row.getCreatedTxn()).isNull();
    assertThat(prepared.status()).isEqualTo("STAGED");
  }

  @Test
  @DisplayName("대출 입금(LOAN_INFLOW)은 장부에 보이지만 NOT_INCOME이다")
  void confirm_loanInflowIsNotIncome() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow row = pendingRow(batch, 1, LocalDate.of(2026, 9, 20),
        EasyTxnDirection.INCOME, 30000000L,
        "{\"primary_disposition\": \"LOAN_INFLOW\", \"business\": \"ACADEMY_CANDIDATE\"}");
    stubTargets(1L, List.of(row), List.of(row), 1L);

    // when
    service.confirm(1L, null);

    // then: 거래는 장부에 존재, 수익 아님
    EasyTransaction txn = row.getCreatedTxn();
    assertThat(txn).isNotNull();
    assertThat(txn.getDirection()).isEqualTo(EasyTxnDirection.INCOME);
    assertThat(txn.getTaxClassification()).isEqualTo(EasyTaxClassification.NOT_INCOME);
    assertThat(txn.getCategory()).isEqualTo("차입금(검토)");
    assertThat(txn.getEvidenceReview()).isEqualTo(EasyEvidenceReview.NEEDS_REVIEW);
    assertThat(txn.getTaxClassification().excludesProfit(txn.getDirection())).isTrue();
  }

  @Test
  @DisplayName("계좌이체 출금은 NOT_EXPENSE, 입금은 NOT_INCOME이다")
  void confirm_interAccountTransferExcludedFromProfit() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow out = pendingRow(batch, 1, LocalDate.of(2026, 9, 21),
        EasyTxnDirection.EXPENSE, 500000L,
        "{\"primary_disposition\": \"INTER_ACCOUNT_TRANSFER\"}");
    EasyImportRow in = pendingRow(batch, 2, LocalDate.of(2026, 9, 21),
        EasyTxnDirection.INCOME, 500000L,
        "{\"primary_disposition\": \"INTER_ACCOUNT_TRANSFER\"}");
    stubTargets(1L, List.of(out, in), List.of(out, in), 2L);

    // when
    service.confirm(1L, null);

    // then: 둘 다 장부에 존재, 손익 제외
    assertThat(out.getCreatedTxn().getTaxClassification())
        .isEqualTo(EasyTaxClassification.NOT_EXPENSE);
    assertThat(in.getCreatedTxn().getTaxClassification())
        .isEqualTo(EasyTaxClassification.NOT_INCOME);
    assertThat(out.getCreatedTxn().getTaxClassification()
        .excludesProfit(EasyTxnDirection.EXPENSE)).isTrue();
    assertThat(in.getCreatedTxn().getTaxClassification()
        .excludesProfit(EasyTxnDirection.INCOME)).isTrue();
  }

  @Test
  @DisplayName("대출이자 판정은 이자비용 후보로 적재된다 (단정 아님)")
  void confirm_loanInterestIsCandidate() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow row = pendingRow(batch, 1, LocalDate.of(2026, 9, 22),
        EasyTxnDirection.EXPENSE, 500000L,
        "{\"primary_disposition\": \"LOAN_INTEREST\"}");
    stubTargets(1L, List.of(row), List.of(row), 1L);

    // when
    service.confirm(1L, null);

    // then
    assertThat(row.getCreatedTxn().getTaxClassification())
        .isEqualTo(EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE);
    assertThat(row.getCreatedTxn().getCategory()).isEqualTo("이자(검토)");
  }

  @Test
  @DisplayName("대출 키워드 fallback — 미분류 입금은 NOT_INCOME, 출금은 REVIEW")
  void confirm_loanKeywordFallback() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow inflow = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(LocalDate.of(2026, 9, 22)).direction(EasyTxnDirection.INCOME).amount(1000000L)
        .counterparty("은행대출").description("입금").payMethod("계좌이체").build();
    inflow.markReady(null, 0.0);
    EasyImportRow outflow = EasyImportRow.builder()
        .batch(batch).rowNo(2)
        .occurredOn(LocalDate.of(2026, 9, 22)).direction(EasyTxnDirection.EXPENSE).amount(200000L)
        .counterparty("은행").description("대출관련 출금").payMethod("계좌이체").build();
    outflow.markReady(null, 0.0);
    stubTargets(1L, List.of(), List.of(inflow, outflow), 2L);

    // when
    service.confirm(1L, null);

    // then
    assertThat(inflow.getCreatedTxn().getTaxClassification())
        .isEqualTo(EasyTaxClassification.NOT_INCOME);
    assertThat(outflow.getCreatedTxn().getTaxClassification())
        .isEqualTo(EasyTaxClassification.REVIEW_REQUIRED);
  }

  @Test
  @DisplayName("장부 중복 fingerprint는 DUPLICATE로 확정 제외된다 (자동합치기 없음)")
  void confirm_ledgerDuplicateExcluded() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow row = pendingRow(batch, 1, LocalDate.of(2026, 9, 23),
        EasyTxnDirection.EXPENSE, 38025L, null);
    EasyTransaction existing = EasyTransaction.builder()
        .business(academy).occurredOn(LocalDate.of(2026, 9, 23))
        .direction(EasyTxnDirection.EXPENSE).amount(38025L)
        .category("미분류").counterparty("거래처").description("적요").payMethod("15:21:07").build();
    String fingerprint = EasyFingerprint.of(1L, LocalDate.of(2026, 9, 23), "EXPENSE", 38025L,
        "거래처", "적요", "15:21:07");
    ReflectionTestUtils.setField(existing, "fingerprint", fingerprint);
    stubTargets(1L, List.of(row), List.of(row), 0L);
    when(txnRepository.findByBusiness_BusinessIdAndFingerprintIsNotNull(1L))
        .thenReturn(List.of(existing));

    // when
    EasyImportBatchResponse confirmed = service.confirm(1L, null);

    // then
    assertThat(row.getStatus()).isEqualTo(EasyImportRowStatus.DUPLICATE);
    assertThat(row.getCreatedTxn()).isNull();
    assertThat(confirmed.confirmedRows()).isZero();
  }

  @Test
  @DisplayName("출판사 확정분은 출판사 장부에 귀속된다 (혼합 금지)")
  void confirm_publishingRowsGoToPublishing() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow row = pendingRow(batch, 1, LocalDate.of(2026, 9, 24),
        EasyTxnDirection.EXPENSE, 990000L,
        "{\"primary_disposition\": \"PUBLISHING_RENT_990K\", \"business\": \"PUBLISHING_CONFIRMED\"}");
    stubTargets(1L, List.of(row), List.of(row), 1L);
    when(businessRepository.findAll()).thenReturn(List.of(academy, publishing));

    // when
    service.confirm(1L, null);

    // then
    assertThat(row.getCreatedTxn()).isNotNull();
    assertThat(row.getCreatedTxn().getBusiness()).isSameAs(publishing);
    assertThat(row.getCreatedTxn().getMemo()).contains("[귀속:PUBLISHING]");
  }

  @Test
  @DisplayName("확정 불가 PENDING(금액 없음)은 ERROR로 남고 사유가 보존된다")
  void confirm_invalidPendingBecomesError() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow broken = pendingRow(batch, 1, LocalDate.of(2026, 9, 23),
        EasyTxnDirection.EXPENSE, null, null);
    EasyImportRow ok = pendingRow(batch, 2, LocalDate.of(2026, 9, 23),
        EasyTxnDirection.EXPENSE, 1000L, null);
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
        1L, EasyImportRowStatus.PENDING)).thenReturn(List.of(broken, ok));
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
        1L, EasyImportRowStatus.READY)).thenReturn(List.of(ok));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(1L))
        .thenReturn(List.of(broken, ok));
    when(rowRepository.countByBatch_BatchIdAndStatus(1L, EasyImportRowStatus.IMPORTED))
        .thenReturn(1L);
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // when
    EasyImportBatchResponse confirmed = service.confirm(1L, null);

    // then: 정상 행 적재 + 파손 행 ERROR (침묵 누락 없음)
    assertThat(broken.getStatus()).isEqualTo(EasyImportRowStatus.ERROR);
    assertThat(broken.getError()).isNotBlank();
    assertThat(ok.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(confirmed.confirmedRows()).isEqualTo(1);
  }

  @Test
  @DisplayName("결제수단의 시각 문자열은 은행/계좌로 정규화된다")
  void confirm_payMethodTimeNormalized() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow row = pendingRow(batch, 1, LocalDate.of(2026, 9, 23),
        EasyTxnDirection.EXPENSE, 38025L,
        "{\"bank\": \"KB\", \"acct\": \"*662\", \"time\": \"15:21:07\"}");
    stubTargets(1L, List.of(row), List.of(row), 1L);

    // when
    service.confirm(1L, null);

    // then
    assertThat(row.getCreatedTxn().getPayMethod()).isEqualTo("은행 KB *662");
  }

  @Test
  @DisplayName("같은 날·같은 금액·다른 시각 거래는 중복 오판하지 않는다")
  void confirm_sameDayDifferentTimeBothPosted() {
    // given
    EasyImportBatch batch = stagedBatch();
    EasyImportRow first = pendingRow(batch, 1, LocalDate.of(2026, 9, 23),
        EasyTxnDirection.EXPENSE, 50000L, "{\"time\": \"10:00:00\"}");
    EasyImportRow second = pendingRow(batch, 2, LocalDate.of(2026, 9, 23),
        EasyTxnDirection.EXPENSE, 50000L, "{\"time\": \"18:00:00\"}");
    stubTargets(1L, List.of(first, second), List.of(first, second), 2L);

    // when
    EasyImportBatchResponse confirmed = service.confirm(1L, null);

    // then
    assertThat(first.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(second.getStatus()).isEqualTo(EasyImportRowStatus.IMPORTED);
    assertThat(first.getCreatedTxn().getFingerprint())
        .isNotEqualTo(second.getCreatedTxn().getFingerprint());
    assertThat(confirmed.confirmedRows()).isEqualTo(2);
    verify(txnRepository, times(2)).save(any(EasyTransaction.class));
  }

  @Test
  @DisplayName("확정 가능 행이 하나도 없으면 에러 (PENDING 포함 조회 후)")
  void confirm_noPostableRowsThrows() {
    // given
    stagedBatch();
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
        1L, EasyImportRowStatus.PENDING)).thenReturn(List.of());
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(
        1L, EasyImportRowStatus.READY)).thenReturn(List.of());

    // when/then
    assertThatThrownBy(() -> service.confirm(1L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("확정할 행이 없습니다");
  }

  @Test
  @DisplayName("extractJsonField — 은행 raw JSON에서 판정·귀속·시각을 읽는다")
  void extractJsonField_readsBankRaw() {
    String raw = "{\"bank\": \"KB\", \"acct\": \"*662\", \"time\": \"15:21:07\", "
        + "\"primary_disposition\": \"LOAN_INFLOW\", \"business\": \"PUBLISHING_CONFIRMED\"}";
    assertThat(EasyImportService.extractJsonField(raw, "primary_disposition"))
        .isEqualTo("LOAN_INFLOW");
    assertThat(EasyImportService.extractJsonField(raw, "business"))
        .isEqualTo("PUBLISHING_CONFIRMED");
    assertThat(EasyImportService.extractJsonField(raw, "time")).isEqualTo("15:21:07");
    assertThat(EasyImportService.extractJsonField(raw, "missing")).isNull();
    assertThat(EasyImportService.extractJsonField(null, "business")).isNull();
    assertThat(EasyImportService.extractJsonField("not json", "business")).isNull();
  }

  @Test
  @DisplayName("excludesProfit — 방향과 세무구분이 맞을 때만 손익 제외")
  void excludesProfit_onlyMatchingDirection() {
    assertThat(EasyTaxClassification.NOT_INCOME.excludesProfit(EasyTxnDirection.INCOME)).isTrue();
    assertThat(EasyTaxClassification.NOT_INCOME.excludesProfit(EasyTxnDirection.EXPENSE)).isFalse();
    assertThat(EasyTaxClassification.NOT_EXPENSE.excludesProfit(EasyTxnDirection.EXPENSE)).isTrue();
    assertThat(EasyTaxClassification.NOT_EXPENSE.excludesProfit(EasyTxnDirection.INCOME)).isFalse();
    assertThat(EasyTaxClassification.REVIEW_REQUIRED.excludesProfit(EasyTxnDirection.INCOME)).isFalse();
    assertThat(EasyTaxClassification.INCOME_INCLUDED.excludesProfit(EasyTxnDirection.INCOME)).isFalse();
    assertThat(EasyTaxClassification.NOT_INCOME.excludesProfit(null)).isFalse();
  }

  @Test
  @DisplayName("이지샵 확정 매핑은 은행 복구 행에도 우선 적용된다")
  void confirm_mappingPrecedenceForBankRows() {
    // given: 외부계정이 이지샵 매핑과 일치하는 PENDING 행
    EasyImportBatch batch = stagedBatch();
    EasyImportRow row = EasyImportRow.builder()
        .batch(batch).rowNo(1)
        .occurredOn(LocalDate.of(2026, 9, 24)).direction(EasyTxnDirection.EXPENSE).amount(3300000L)
        .counterparty("건물주").description("월세").payMethod("계좌이체")
        .externalAccount("임차료(월세)").build();
    stubTargets(1L, List.of(row), List.of(row), 1L);
    when(mappingRepository.findFirstByExternalCode("임차료(월세)")).thenReturn(Optional.of(
        f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping
            .builder().account(rent).externalCode("임차료(월세)").build()));

    // when
    service.confirm(1L, null);

    // then: 이지샵 매핑 계정으로 분류 적재
    assertThat(row.getCreatedTxn().getAccount()).isSameAs(rent);
    assertThat(row.getCreatedTxn().getCategory()).isEqualTo("임차료");
    assertThat(row.getCreatedTxn().getEvidenceReview()).isNull();
  }

  @Test
  @DisplayName("stage→confirm 은행형 플로우 — 2026-09-14 이후 누락 복구 형태")
  void stageThenConfirm_bankShapedBatch() {
    // given: stage()를 거친 뒤 confirm하는 정상 경로 (분류 미확정 포함)
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(batchRepository.save(any(EasyImportBatch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rowRepository.save(any(EasyImportRow.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EasyImportBatchResponse staged = service.stage(new EasyImportBatchRequest(
        1L, "bank.csv", "BANK_CSV", List.of(
            new EasyImportRowInput("2026-09-18", "EXPENSE", 100000L, "상점", "물품",
                "계좌이체", null, null, null, null),
            new EasyImportRowInput("2026-09-19", "INCOME", 29000L, "고객", "입금",
                "계좌이체", null, null, null, null))));

    assertThat(staged.rows()).allMatch(row -> "READY".equals(row.status()));
  }
}
