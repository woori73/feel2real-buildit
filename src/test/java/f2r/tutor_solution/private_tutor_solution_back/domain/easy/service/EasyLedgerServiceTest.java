package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
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

/**
 * 장부 요약 — 사업체 분리·전체 합산·미수금 + 거래 CRUD 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyLedgerService 장부 테스트")
class EasyLedgerServiceTest {

  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyAccountRepository accountRepository;
  @Mock EasyTuitionPaymentRepository paymentRepository;
  @Mock EasyStudentRepository studentRepository;
  @Mock EasyJournalService journalService;
  @Mock EasyCorrectionService correctionService;

  private EasyLedgerService service;
  private EasyBusiness academy;
  private EasyBusiness publishing;

  @BeforeEach
  void setUp() {
    service = new EasyLedgerService(
        txnRepository, businessRepository, accountRepository, paymentRepository, studentRepository,
        journalService, correctionService);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    publishing = EasyBusiness.builder().code("PUBLISHING").name("출판사").active(true).sortOrder(2).build();
  }

  private EasyTransaction txn(EasyBusiness business, EasyTxnDirection direction, long amount) {
    return EasyTransaction.builder()
        .business(business).occurredOn(LocalDate.of(2026, 9, 5)).direction(direction)
        .amount(amount).category("수강료").build();
  }

  @Test
  @DisplayName("사업체별 요약 — 학원 거래만 집계, 출판사 섞이지 않음")
  void summary_perBusiness_separated() {
    Long academyId = 1L;
    when(businessRepository.findById(academyId)).thenReturn(Optional.of(academy));
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        academyId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .thenReturn(List.of(
            txn(academy, EasyTxnDirection.INCOME, 500000L),
            txn(academy, EasyTxnDirection.EXPENSE, 200000L)));
    when(paymentRepository.findByBusiness_BusinessId(academyId)).thenReturn(List.of());

    EasyDashboardResponse summary = service.summary(academyId, YearMonth.of(2026, 9));

    assertThat(summary.income()).isEqualTo(500000L);
    assertThat(summary.expense()).isEqualTo(200000L);
    assertThat(summary.profit()).isEqualTo(300000L);
    assertThat(summary.businessName()).isEqualTo("학원");
  }

  @Test
  @DisplayName("전체 요약 — 두 사업체 합산")
  void summary_all_summed() {
    when(txnRepository.findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .thenReturn(List.of(
            txn(academy, EasyTxnDirection.INCOME, 500000L),
            txn(publishing, EasyTxnDirection.INCOME, 300000L),
            txn(academy, EasyTxnDirection.EXPENSE, 100000L)));
    when(paymentRepository.findAll()).thenReturn(List.of());
    when(txnRepository.findTop10ByOrderByOccurredOnDescTxnIdDesc()).thenReturn(List.of());

    EasyDashboardResponse summary = service.summary(null, YearMonth.of(2026, 9));

    assertThat(summary.income()).isEqualTo(800000L);
    assertThat(summary.expense()).isEqualTo(100000L);
    assertThat(summary.profit()).isEqualTo(700000L);
  }

  @Test
  @DisplayName("미수금 — 잔액 합산 + 학생별 집계")
  void summary_receivable() {
    EasyStudent student = EasyStudent.builder().name("김OO").school("압구정중").grade((byte) 2).build();
    when(txnRepository.findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(List.of());
    when(paymentRepository.findAll()).thenReturn(List.of(
        EasyTuitionPayment.builder().student(student).business(academy)
            .targetMonth("2026-09").billedAmount(300000L).paidAmount(100000L).build(),
        EasyTuitionPayment.builder().student(student).business(academy)
            .targetMonth("2026-08").billedAmount(300000L).paidAmount(300000L).build()));
    when(txnRepository.findTop10ByOrderByOccurredOnDescTxnIdDesc()).thenReturn(List.of());

    EasyDashboardResponse summary = service.summary(null, YearMonth.of(2026, 9));

    assertThat(summary.receivable()).isEqualTo(200000L);
    assertThat(summary.overdueStudents()).hasSize(1);
    assertThat(summary.overdueStudents().get(0).balance()).isEqualTo(200000L);
  }

  @Test
  @DisplayName("수입 등록 성공 + 음수 금액 거부")
  void create_income_ok_negative_rejected() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.createTxn(new EasyTxnRequest(
        1L, LocalDate.of(2026, 9, 5), "INCOME", 500000L,
        "수강료", "김OO", "9월 수강료", "계좌이체", null, null));

    assertThat(response.amount()).isEqualTo(500000L);
    assertThat(response.direction()).isEqualTo("INCOME");

    assertThatThrownBy(() -> service.createTxn(new EasyTxnRequest(
        1L, LocalDate.of(2026, 9, 5), "EXPENSE", -1000L,
        "기타", "", "", "", null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("지출 등록 + 수정 + 삭제")
  void expense_update_delete() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var created = service.createTxn(new EasyTxnRequest(
        1L, LocalDate.of(2026, 9, 5), "EXPENSE", 100000L,
        "임대료", "건물주", "9월 임대료", "계좌이체", null, null));
    assertThat(created.direction()).isEqualTo("EXPENSE");

    EasyTransaction stored = txn(academy, EasyTxnDirection.EXPENSE, 100000L);
    when(txnRepository.findById(7L)).thenReturn(Optional.of(stored));
    var updated = service.updateTxn(7L, new EasyTxnRequest(
        1L, LocalDate.of(2026, 9, 6), "EXPENSE", 120000L,
        "임대료", "건물주", "9월 임대료", "계좌이체", null, null));
    assertThat(updated.amount()).isEqualTo(120000L);

    service.deleteTxn(7L);
    verify(txnRepository).delete(stored);
  }

  @Test
  @DisplayName("사업체 미지정 등록 거부")
  void create_withoutBusiness_rejected() {
    assertThatThrownBy(() -> service.createTxn(new EasyTxnRequest(
        null, LocalDate.of(2026, 9, 5), "INCOME", 1000L,
        "기타", "", "", "", null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(txnRepository, never()).save(any());
  }

  @Test
  @DisplayName("계정·공급가액·부가세·증빙 저장 + 잘못된 증빙 거부")
  void create_withAccountSplit_ok_badEvidence_rejected() {
    f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount account =
        f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount.builder()
            .code("INCOME_TUITION").name("수강료")
            .direction(f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity
                .EasyAccountDirection.INCOME)
            .sortOrder(1).active(true).build();
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
    when(txnRepository.save(any(EasyTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.createTxn(new EasyTxnRequest(
        1L, LocalDate.of(2026, 9, 5), "INCOME", 330000L,
        "수강료", "김OO", "9월 수강료", "계좌이체", null, null,
        10L, 300000L, 30000L, "CREDIT_CARD"));

    assertThat(response.accountName()).isEqualTo("수강료");
    assertThat(response.supplyAmount()).isEqualTo(300000L);
    assertThat(response.vatAmount()).isEqualTo(30000L);
    assertThat(response.evidenceType()).isEqualTo("CREDIT_CARD");

    assertThatThrownBy(() -> service.createTxn(new EasyTxnRequest(
        1L, LocalDate.of(2026, 9, 5), "INCOME", 1000L,
        "기타", "", "", "", null, null,
        null, null, null, "WRONG")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("CSV export — BOM 헤더 + 행")
  void exportCsv_headerAndRows() {
    when(txnRepository.findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31)))
        .thenReturn(List.of(txn(academy, EasyTxnDirection.INCOME, 500000L)));

    String csv = service.exportCsv(null, null, null);

    assertThat(csv).startsWith("\uFEFF");
    assertThat(csv).contains("거래일,사업체,구분,금액,공급가액,부가세,계정,분류,거래처,적요,결제수단,증빙,메모");
    assertThat(csv).contains("수입").contains("500000");
  }

  @Test
  @DisplayName("학생 수는 같은 scope로 — 전체면 재원 수, 사업체면 수납 이력 학생 수")
  void summary_studentCount_scoped() {
    when(txnRepository.findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(List.of());
    when(txnRepository.findTop10ByOrderByOccurredOnDescTxnIdDesc()).thenReturn(List.of());
    when(paymentRepository.findAll()).thenReturn(List.of());
    when(studentRepository.countByStatus(EasyStudentStatus.ENROLLED)).thenReturn(12L);

    assertThat(service.summary(null, YearMonth.of(2026, 9)).studentCount()).isEqualTo(12L);

    EasyStudent s1 = EasyStudent.builder().name("A").build();
    EasyStudent s2 = EasyStudent.builder().name("B").build();
    ReflectionTestUtils.setField(s1, "studentId", 101L);
    ReflectionTestUtils.setField(s2, "studentId", 102L);
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(List.of());
    when(txnRepository.findTop10ByBusiness_BusinessIdInOrderByOccurredOnDescTxnIdDesc(List.of(1L)))
        .thenReturn(List.of());
    when(paymentRepository.findByBusiness_BusinessId(1L)).thenReturn(List.of(
        EasyTuitionPayment.builder().student(s1).business(academy)
            .targetMonth("2026-09").billedAmount(300000L).paidAmount(300000L).build(),
        EasyTuitionPayment.builder().student(s1).business(academy)
            .targetMonth("2026-08").billedAmount(300000L).paidAmount(0L).build(),
        EasyTuitionPayment.builder().student(s2).business(academy)
            .targetMonth("2026-09").billedAmount(300000L).paidAmount(0L).build()));

    EasyDashboardResponse scoped = service.summary(1L, YearMonth.of(2026, 9));
    // 수납 이력 학생 2명 (s1이 2건이어도 1명으로 집계)
    assertThat(scoped.studentCount()).isEqualTo(2L);
    // 미수금은 잔액>0만: s1 8월 30만 + s2 9월 30만
    assertThat(scoped.receivable()).isEqualTo(600000L);
    // 미수 학생은 잔액 내림차순
    assertThat(scoped.overdueStudents()).hasSize(2);
    assertThat(scoped.overdueStudents().get(0).balance()).isEqualTo(300000L);
  }

  @Test
  @DisplayName("미수금·미수목록은 선택 사업체 수납만 집계 (다른 사업체 섞이지 않음)")
  void summary_receivable_businessScoped() {
    EasyStudent student = EasyStudent.builder().name("김OO").build();
    when(businessRepository.findById(2L)).thenReturn(Optional.of(publishing));
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        2L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(List.of());
    when(txnRepository.findTop10ByBusiness_BusinessIdInOrderByOccurredOnDescTxnIdDesc(List.of(2L)))
        .thenReturn(List.of());
    // 출판사 scope 조회는 출판사 수납만 본다 — 학원 미수금이 섞이지 않음
    when(paymentRepository.findByBusiness_BusinessId(2L)).thenReturn(List.of(
        EasyTuitionPayment.builder().student(student).business(publishing)
            .targetMonth("2026-09").billedAmount(100000L).paidAmount(100000L).build()));

    EasyDashboardResponse summary = service.summary(2L, YearMonth.of(2026, 9));

    verify(paymentRepository).findByBusiness_BusinessId(2L);
    verify(paymentRepository, never()).findAll();
    assertThat(summary.receivable()).isZero();
    assertThat(summary.overdueStudents()).isEmpty();
  }

  @Test
  @DisplayName("수정 시 수정로그 기록 + 재분개 위임")
  void update_logsCorrectionAndReposts() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    EasyTransaction stored = txn(academy, EasyTxnDirection.EXPENSE, 100000L);
    ReflectionTestUtils.setField(stored, "txnId", 7L);
    when(txnRepository.findById(7L)).thenReturn(Optional.of(stored));

    service.updateTxn(7L, new EasyTxnRequest(
        1L, LocalDate.of(2026, 9, 6), "EXPENSE", 120000L,
        "임대료", "건물주", "9월 임대료", "계좌이체", null, null));

    verify(correctionService).record(eq(academy), eq(stored), eq("UPDATE"),
        any(), any(), any());
    verify(journalService).repost(stored);
  }

  @Test
  @DisplayName("삭제 시 분개 void + 삭제 로그")
  void delete_voidsJournalAndLogs() {
    EasyTransaction stored = txn(academy, EasyTxnDirection.EXPENSE, 100000L);
    when(txnRepository.findById(7L)).thenReturn(Optional.of(stored));

    service.deleteTxn(7L);

    verify(journalService).voidFor(stored);
    verify(correctionService).record(eq(academy), eq(stored), eq("DELETE"),
        any(), any(), any());
    verify(txnRepository).delete(stored);
  }
}
