package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

/**
 * 장부 요약 — 사업체 분리·전체 합산·미수금 + 거래 CRUD 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyLedgerService 장부 테스트")
class EasyLedgerServiceTest {

  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyTuitionPaymentRepository paymentRepository;
  @Mock EasyStudentRepository studentRepository;

  private EasyLedgerService service;
  private EasyBusiness academy;
  private EasyBusiness publishing;

  @BeforeEach
  void setUp() {
    service = new EasyLedgerService(txnRepository, businessRepository, paymentRepository, studentRepository);
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
    EasyStudent student = EasyStudent.builder().name("김OO").school("압구정중").grade(2).build();
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
}
