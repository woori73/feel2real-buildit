package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyReviewItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyWithholdingRecordRepository;
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

  private EasyAuditService service;
  private EasyBusiness academy;
  private EasyBusiness publishing;

  @BeforeEach
  void setUp() {
    service = new EasyAuditService(
        reviewRepository, txnRepository, businessRepository, accountRepository,
        batchRepository, rowRepository, withholdingRepository, closingRepository);
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

  @Test
  @DisplayName("마감 — 미확인 있으면 차단, 강제 마감·재개방 가능")
  void closing_blocked_thenForced_thenReopened() {
    EasyAnnualClosing closing = new EasyAnnualClosing(academy, 2026);
    ReflectionTestUtils.setField(closing, "closingId", 5L);
    when(closingRepository.findById(5L)).thenReturn(Optional.of(closing));
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(reviewRepository.countByBusiness_BusinessIdAndKindAndStatus(
        any(), any(), any())).thenReturn(1L);

    assertThatThrownBy(() -> service.closeClosing(5L, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("미확인 항목");

    var closed = service.closeClosing(5L, "6건 확인 후 강제", true);
    assertThat(closed.status()).isEqualTo("CLOSED");
    assertThat(closed.checklistSnapshot()).contains("\"forced\":true");

    var reopened = service.reopenClosing(5L);
    assertThat(reopened.status()).isEqualTo("OPEN");
    assertThat(closing.getStatus()).isEqualTo(EasyClosingStatus.OPEN);
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
