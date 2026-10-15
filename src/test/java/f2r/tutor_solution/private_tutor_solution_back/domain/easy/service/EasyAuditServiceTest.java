package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewResolveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWithholdingRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAnnualClosing;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClosingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCorrectionLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyReviewItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyWithholdingRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("EasyAuditService 감사 기반 테스트")
class EasyAuditServiceTest {

  @Mock EasyReviewItemRepository reviewRepository;
  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyAccountRepository accountRepository;
  @Mock EasyImportBatchRepository batchRepository;
  @Mock EasyImportRowRepository rowRepository;
  @Mock EasyWithholdingRecordRepository withholdingRepository;
  @Mock EasyAnnualClosingRepository closingRepository;
  @Mock EasyImportService importService;
  @Mock EasyJournalService journalService;
  @Mock EasyCorrectionService correctionService;
  @Mock EasyCorrectionLogRepository correctionLogRepository;

  private EasyAuditService service;
  private EasyBusiness academy;
  private EasyBusiness publishing;

  @BeforeEach
  void setUp() {
    service = new EasyAuditService(
        reviewRepository, txnRepository, businessRepository, accountRepository,
        batchRepository, rowRepository, withholdingRepository, closingRepository,
        importService, journalService, correctionService, correctionLogRepository);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    publishing = EasyBusiness.builder().code("PUBLISHING").name("출판사").active(true)
        .sortOrder(2).build();
    ReflectionTestUtils.setField(academy, "businessId", 1L);
    ReflectionTestUtils.setField(publishing, "businessId", 2L);
  }

  private EasyTransaction txn(EasyBusiness business, Long txnId, EasyTxnDirection direction,
      long amount, String description, EasyAccount account, EasyEvidenceType evidence) {
    EasyTransaction txn = EasyTransaction.builder()
        .business(business).occurredOn(LocalDate.of(2026, 8, 23)).direction(direction)
        .amount(amount).category("기타").counterparty("").description(description)
        .payMethod("계좌이체").account(account).evidenceType(evidence).build();
    ReflectionTestUtils.setField(txn, "txnId", txnId);
    return txn;
  }

  @Test
  @DisplayName("Workspace 재승인 — 요청 동일성을 증명할 수 없어 409")
  void workspaceResolve_processedReviewIsConflict() {
    EasyTransaction stored = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        100000L, "강사료", null, null);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.WITHHOLDING).txn(stored)
        .title("확인").build();
    item.resolve("처리", "admin");
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findLockedByReviewIdAndBusiness_BusinessId(9L, 1L))
        .thenReturn(Optional.of(item));

    assertThatThrownBy(() -> service.resolveWorkspace(
        1L, 9L, new EasyReviewResolveRequest("다시", null, null, null, null, false),
        "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
            .isEqualTo(409));
  }

  @Test
  @DisplayName("Workspace 동시 승인 — 검토 행을 PESSIMISTIC_WRITE로 직렬화한다")
  void workspaceResolve_usesPessimisticWriteLock() throws Exception {
    var method = EasyReviewItemRepository.class.getMethod(
        "findLockedByReviewIdAndBusiness_BusinessId", Long.class, Long.class);

    assertThat(method.getAnnotation(Lock.class)).isNotNull();
    assertThat(method.getAnnotation(Lock.class).value())
        .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  @DisplayName("Workspace 승인 — 원 사업체 회계기간이 CLOSED면 409")
  void workspaceResolve_checksSourceClosing() {
    EasyTransaction stored = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        100000L, "강사료", null, null);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.WITHHOLDING).txn(stored)
        .title("원천징수 확인").build();
    EasyAnnualClosing sourceClosing = EasyAnnualClosing.builder()
        .business(academy).fiscalYear(2026).build();
    sourceClosing.close("{}", "완료");
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findLockedByReviewIdAndBusiness_BusinessId(9L, 1L))
        .thenReturn(Optional.of(item));
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2026))
        .thenReturn(Optional.of(sourceClosing));

    assertThatThrownBy(() -> service.resolveWorkspace(
        1L, 9L, new EasyReviewResolveRequest("승인", null, null, null, "CHECKED", false),
        "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
            .isEqualTo(409));
  }

  @Test
  @DisplayName("Workspace 사업체 이동 — 원 사업체와 대상 사업체 양쪽 마감 검증")
  void workspaceResolve_checksTargetClosing() {
    EasyTransaction stored = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        100000L, "강사료", null, null);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.BUSINESS).txn(stored)
        .title("사업체 확인").build();
    EasyAnnualClosing targetClosing = EasyAnnualClosing.builder()
        .business(publishing).fiscalYear(2026).build();
    targetClosing.close("{}", "완료");
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(publishing));
    when(reviewRepository.findLockedByReviewIdAndBusiness_BusinessId(9L, 1L))
        .thenReturn(Optional.of(item));
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(1L, 2026))
        .thenReturn(Optional.empty());
    when(closingRepository.findByBusiness_BusinessIdAndFiscalYear(2L, 2026))
        .thenReturn(Optional.of(targetClosing));

    assertThatThrownBy(() -> service.resolveWorkspace(
        1L, 9L, new EasyReviewResolveRequest("이동", 2L, null, null, null, false),
        "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
            .isEqualTo(409));
  }

  @Test
  @DisplayName("Workspace 보류 — 사업체 복합 조회 후 검토·장부를 변경하지 않는다")
  void workspaceHold_isBusinessScopedAndReadOnly() {
    EasyTransaction stored = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        100000L, "강사료", null, null);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.WITHHOLDING).txn(stored)
        .title("원천징수 확인").build();
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findByReviewIdAndBusiness_BusinessId(9L, 1L))
        .thenReturn(Optional.of(item));

    var response = service.holdWorkspace(1L, 9L);

    assertThat(response.status()).isEqualTo(EasyReviewStatus.OPEN.name());
    verify(reviewRepository).findByReviewIdAndBusiness_BusinessId(9L, 1L);
    verifyNoInteractions(txnRepository, journalService, correctionService);
  }

  @Test
  @DisplayName("Workspace 가져오기 승인 — 배치 확정과 검토 종료를 함께 처리한다")
  void workspaceResolve_importReviewConfirmsBatch() {
    var batch = batch(21L);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.IMPORT_UNCONFIRMED).batch(batch)
        .title("장부 미입력").build();
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findLockedByReviewIdAndBusiness_BusinessId(9L, 1L))
        .thenReturn(Optional.of(item));

    var response = service.resolveWorkspace(
        1L, 9L, new EasyReviewResolveRequest("관리자 확인", null, null, null, null, false),
        "admin");

    verify(importService).confirm(21L, null);
    assertThat(response.status()).isEqualTo(EasyReviewStatus.RESOLVED.name());
  }

  @Test
  @DisplayName("rebuild — 미분류·증빙 확인 필요 생성 + 확정분 제외")
  void rebuild_createsItems_idempotent() {
    EasyAccount salary = EasyAccount.builder().code("EXPENSE_SALARY").name("급여/강사비")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(11).active(true).build();
    EasyTransaction unclassified = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        74000L, "8월 운영비", null, null);
    EasyTransaction confirmed = txn(academy, 102L, EasyTxnDirection.EXPENSE,
        100000L, "임대료", salary, EasyEvidenceType.TAX_INVOICE);
    when(businessRepository.findAll()).thenReturn(List.of(academy));
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        1L, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31)))
        .thenReturn(List.of(unclassified, confirmed));
    when(txnRepository.findByBusiness_BusinessIdAndFingerprintIsNotNull(1L))
        .thenReturn(List.of());
    when(batchRepository.findAllByBusiness_BusinessIdOrderByBatchIdDesc(1L))
        .thenReturn(List.of());
    when(reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
        1L, EasyReviewKind.UNCLASSIFIED, EasyReviewStatus.OPEN, 101L)).thenReturn(false);
    when(reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
        1L, EasyReviewKind.EVIDENCE, EasyReviewStatus.OPEN, 101L)).thenReturn(false);
    when(reviewRepository.save(any(EasyReviewItem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.rebuild(null);

    // 미분류 1 + 증빙 1 (확정 거래는 제외)
    verify(reviewRepository,
        org.mockito.Mockito.times(2)).save(any(EasyReviewItem.class));
  }

  @Test
  @DisplayName("resolve — 사업장 이동 + 계정 연결 + 검토 상태 반영")
  void resolve_movesBusinessAndLinksAccount() {
    EasyTransaction stored = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        74000L, "8월 운영비", null, null);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.UNCLASSIFIED).txn(stored)
        .title("미분류").build();
    EasyAccount supplies = EasyAccount.builder().code("EXPENSE_SUPPLIES").name("소모품")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(16).active(true).build();
    when(reviewRepository.findById(9L)).thenReturn(Optional.of(item));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(publishing));
    when(accountRepository.findById(10L)).thenReturn(Optional.of(supplies));

    var response = service.resolve(9L, new EasyReviewResolveRequest(
        "출판사 소모품으로 확인", 2L, 10L, "VERIFIED", "NONE", null), "admin");

    assertThat(response.status()).isEqualTo("RESOLVED");
    assertThat(stored.getBusiness().getBusinessId()).isEqualTo(2L);
    assertThat(stored.getAccount().getName()).isEqualTo("소모품");
    assertThat(stored.getEvidenceReview().name()).isEqualTo("VERIFIED");
    assertThat(stored.getFingerprint()).isEqualTo(EasyFingerprint.of(
        2L, LocalDate.of(2026, 8, 23), "EXPENSE", 74000L, "", "8월 운영비", "계좌이체"));
    verify(correctionService).record(eq(publishing), eq(stored), eq("RESOLVE"),
        any(), any(), eq("admin"));
    verify(journalService).repost(stored);
  }

  @Test
  @DisplayName("수정 미리보기 — 적용 없이 before/after와 변경점만 반환")
  void preview_showsChangesWithoutApplying() {
    EasyTransaction stored = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        74000L, "8월 운영비", null, null);
    EasyAccount supplies = EasyAccount.builder().code("EXPENSE_SUPPLIES").name("소모품")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(16).active(true).build();
    when(txnRepository.findById(101L)).thenReturn(Optional.of(stored));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(publishing));
    when(accountRepository.findById(10L)).thenReturn(Optional.of(supplies));

    var preview = service.previewCorrection(101L, new EasyReviewResolveRequest(
        "확인", 2L, 10L, "VERIFIED", null, null));

    assertThat(preview.changes()).anyMatch(change -> change.contains("사업장"));
    assertThat(preview.changes()).anyMatch(change -> change.contains("계정"));
    assertThat(preview.warnings()).isNotEmpty();
    assertThat(preview.after()).contains("\"businessId\":2");
    // 적용되지 않음 — 원본 그대로
    assertThat(stored.getBusiness().getBusinessId()).isEqualTo(1L);
    assertThat(stored.getAccount()).isNull();
  }

  @Test
  @DisplayName("resolve ignore — 장부 변경 없이 닫힘")
  void resolve_ignore_closesWithoutTouchingTxn() {
    EasyTransaction stored = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        74000L, "8월 운영비", null, null);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.EVIDENCE).txn(stored)
        .title("증빙").build();
    when(reviewRepository.findById(9L)).thenReturn(Optional.of(item));

    var response = service.resolve(9L,
        new EasyReviewResolveRequest("사업 무관", null, null, null, null, true), "admin");

    assertThat(response.status()).isEqualTo("IGNORED");
    assertThat(stored.getBusiness().getBusinessId()).isEqualTo(1L);
    assertThat(stored.getAccount()).isNull();
  }

  private f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch batch(
      Long batchId) {
    f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch batch =
        f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch.builder()
            .business(academy).sourceName("bank.csv").sourceKind("BANK_CSV").totalRows(2).build();
    ReflectionTestUtils.setField(batch, "batchId", batchId);
    return batch;
  }

  private f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow batchRow(
      f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch batch,
      java.time.LocalDate occurredOn) {
    return f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow
        .builder()
        .batch(batch).rowNo(1).occurredOn(occurredOn)
        .direction(EasyTxnDirection.EXPENSE).amount(50000L)
        .counterparty("").description("").payMethod("")
        .supplyAmount(null).vatAmount(null).evidenceType(null).rawData(null).build();
  }

  private EasyAnnualClosing closing(Long closingId, int fiscalYear) {
    EasyAnnualClosing closing = new EasyAnnualClosing(academy, fiscalYear);
    ReflectionTestUtils.setField(closing, "closingId", closingId);
    return closing;
  }

  @Test
  @DisplayName("마감 — 같은 연도 미확인 있으면 차단, 강제 마감·재개방 가능")
  void closing_blocked_thenForced_thenReopened() {
    EasyTransaction open2026 = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        74000L, "8월 운영비", null, null);
    ReflectionTestUtils.setField(open2026, "occurredOn", LocalDate.of(2026, 8, 23));
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.UNCLASSIFIED).txn(open2026)
        .title("미분류").build();
    EasyAnnualClosing closing = closing(5L, 2026);
    when(closingRepository.findById(5L)).thenReturn(Optional.of(closing));
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(
        1L, EasyReviewStatus.OPEN)).thenReturn(List.of(item));

    assertThatThrownBy(() -> service.closeClosing(5L, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2026년 미확인 항목 1건");

    var closed = service.closeClosing(5L, "1건 확인 후 강제", true);
    assertThat(closed.status()).isEqualTo("CLOSED");
    assertThat(closed.checklistSnapshot()).contains("\"forced\":true");

    var reopened = service.reopenClosing(5L);
    assertThat(reopened.status()).isEqualTo("OPEN");
    assertThat(closing.getStatus()).isEqualTo(EasyClosingStatus.OPEN);
  }

  @Test
  @DisplayName("마감 — 2026 OPEN이 있어도 2025 마감은 차단하지 않음")
  void closing_2026open_doesNotBlock2025() {
    EasyTransaction open2026 = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        74000L, "8월 운영비", null, null);
    ReflectionTestUtils.setField(open2026, "occurredOn", LocalDate.of(2026, 3, 1));
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.UNCLASSIFIED).txn(open2026)
        .title("미분류").build();
    EasyAnnualClosing closing2025 = closing(5L, 2025);
    when(closingRepository.findById(5L)).thenReturn(Optional.of(closing2025));
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(
        1L, EasyReviewStatus.OPEN)).thenReturn(List.of(item));

    var closed = service.closeClosing(5L, null, false);

    assertThat(closed.status()).isEqualTo("CLOSED");
    assertThat(closed.checklistSnapshot()).contains("\"fiscalYear\":2025");
    assertThat(closed.checklistSnapshot()).contains("\"UNCLASSIFIED\":0");
  }

  @Test
  @DisplayName("마감 — 2025 OPEN은 2025 마감을 차단함")
  void closing_2025open_blocks2025() {
    EasyTransaction open2025 = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        74000L, "1월 운영비", null, null);
    ReflectionTestUtils.setField(open2025, "occurredOn", LocalDate.of(2025, 1, 15));
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.EVIDENCE).txn(open2025)
        .title("증빙").build();
    EasyAnnualClosing closing2025 = closing(5L, 2025);
    when(closingRepository.findById(5L)).thenReturn(Optional.of(closing2025));
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(
        1L, EasyReviewStatus.OPEN)).thenReturn(List.of(item));

    assertThatThrownBy(() -> service.closeClosing(5L, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2025년 미확인 항목 1건");
  }

  @Test
  @DisplayName("마감 — 귀속연도 미확정 staging은 임의 배정 없이 모든 연도를 차단함")
  void closing_undatedBatch_blocksAllYears_withoutAssignment() {
    f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch batch =
        batch(21L);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.IMPORT_UNCONFIRMED).batch(batch)
        .title("장부 미입력").build();
    EasyAnnualClosing closing2025 = closing(5L, 2025);
    when(closingRepository.findById(5L)).thenReturn(Optional.of(closing2025));
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(
        1L, EasyReviewStatus.OPEN)).thenReturn(List.of(item));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(21L))
        .thenReturn(List.of(batchRow(batch, null)));

    assertThatThrownBy(() -> service.closeClosing(5L, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("귀속연도 미확정 1건");
  }

  @Test
  @DisplayName("마감 — 날짜 있는 배치 미확정은 해당 연도만 차단함")
  void closing_datedBatch_onlyBlocksItsYear() {
    f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch batch =
        batch(21L);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(academy).kind(EasyReviewKind.IMPORT_UNCONFIRMED).batch(batch)
        .title("장부 미입력").build();
    EasyAnnualClosing closing2025 = closing(5L, 2025);
    EasyAnnualClosing closing2026 = closing(6L, 2026);
    when(closingRepository.findById(5L)).thenReturn(Optional.of(closing2025));
    when(closingRepository.findById(6L)).thenReturn(Optional.of(closing2026));
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(
        1L, EasyReviewStatus.OPEN)).thenReturn(List.of(item));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(21L))
        .thenReturn(List.of(batchRow(batch, LocalDate.of(2025, 5, 1))));

    assertThatThrownBy(() -> service.closeClosing(5L, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2025년 미확인 항목 1건");

    var closed2026 = service.closeClosing(6L, null, false);
    assertThat(closed2026.status()).isEqualTo("CLOSED");
  }

  @Test
  @DisplayName("resolve — 공용 계정은 사업장 이동 없이도 연결 가능 (isolation은 거래 business가 담당)")
  void resolve_sharedAccount_linksWithoutBusinessMove() {
    // easy_accounts는 사업체 FK 없는 공용 마스터(V974) — 계정·사업장 조합 제한 없음.
    EasyTransaction stored = txn(publishing, 102L, EasyTxnDirection.EXPENSE,
        100000L, "소모품", null, null);
    EasyReviewItem item = EasyReviewItem.builder()
        .business(publishing).kind(EasyReviewKind.UNCLASSIFIED).txn(stored)
        .title("미분류").build();
    EasyAccount supplies = EasyAccount.builder().code("EXPENSE_SUPPLIES").name("소모품")
        .direction(EasyAccountDirection.EXPENSE).sortOrder(16).active(true).build();
    when(reviewRepository.findById(9L)).thenReturn(Optional.of(item));
    when(accountRepository.findById(10L)).thenReturn(Optional.of(supplies));

    var response = service.resolve(9L, new EasyReviewResolveRequest(
        "출판사 소모품으로 확인", null, 10L, null, null, null), "admin");

    assertThat(response.status()).isEqualTo("RESOLVED");
    assertThat(stored.getBusiness().getBusinessId()).isEqualTo(2L);
    assertThat(stored.getAccount().getName()).isEqualTo("소모품");
  }

  @Test
  @DisplayName("원천징수 — 음수 거부 + 타 사업체 거래 연결 거부")
  void withholding_validations() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    EasyTransaction otherBiz = txn(publishing, 7L, EasyTxnDirection.EXPENSE,
        3000000L, "개발비", null, null);
    when(txnRepository.findById(7L)).thenReturn(Optional.of(otherBiz));

    assertThatThrownBy(() -> service.createWithholding(new EasyWithholdingRequest(
        1L, "백승민", -100L, null, null, null, null, false, false, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.createWithholding(new EasyWithholdingRequest(
        1L, "백승민", 3000000L, 2901000L, 99000L,
        LocalDate.of(2026, 7, 25), 7L, false, false, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("다른 사업체");
    verify(withholdingRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  @DisplayName("사업장 확인 표시는 중복 등록되지 않음")
  void flagBusiness_duplicate_rejected() {
    EasyTransaction stored = txn(academy, 101L, EasyTxnDirection.EXPENSE,
        74000L, "출금", null, null);
    when(txnRepository.findById(101L)).thenReturn(Optional.of(stored));
    when(reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
        1L, EasyReviewKind.BUSINESS, EasyReviewStatus.OPEN, 101L)).thenReturn(true);

    assertThatThrownBy(() -> service.flagBusinessCheck(101L, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
