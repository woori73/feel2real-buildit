package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyExecutiveSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EasyExecutiveService 경영 요약 테스트")
class EasyExecutiveServiceTest {

  @Mock EasyTransactionRepository txnRepository;
  @Mock EasyBusinessRepository businessRepository;
  @Mock EasyTuitionPaymentRepository paymentRepository;
  @Mock EasyStudentRepository studentRepository;

  private EasyExecutiveService service;
  private EasyBusiness academy;
  private EasyBusiness publishing;

  @BeforeEach
  void setUp() {
    service = new EasyExecutiveService(
        txnRepository, businessRepository, paymentRepository, studentRepository);
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    publishing = EasyBusiness.builder().code("PUBLISHING").name("출판사").active(true)
        .sortOrder(2).build();
    org.springframework.test.util.ReflectionTestUtils.setField(academy, "businessId", 1L);
    org.springframework.test.util.ReflectionTestUtils.setField(publishing, "businessId", 2L);
  }

  private EasyTransaction txn(EasyBusiness business, EasyTxnDirection direction, long amount) {
    return EasyTransaction.builder()
        .business(business).occurredOn(LocalDate.now()).direction(direction)
        .amount(amount).category("수강료").build();
  }

  private void stubScope(EasyBusiness business, Long id,
      long curIncome, long curExpense, long prevIncome, long prevExpense) {
    LocalDate today = LocalDate.now();
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        id, LocalDate.of(today.getYear(), 1, 1), today))
        .thenReturn(List.of(
            txn(business, EasyTxnDirection.INCOME, curIncome),
            txn(business, EasyTxnDirection.EXPENSE, curExpense)));
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        id, LocalDate.of(today.getYear() - 1, 1, 1), today.minusYears(1)))
        .thenReturn(List.of(
            txn(business, EasyTxnDirection.INCOME, prevIncome),
            txn(business, EasyTxnDirection.EXPENSE, prevExpense)));
  }

  @Test
  @DisplayName("전체 요약 — 올해/작년 동기 비교 + 사업장 합산 + 종소세 자리 미계산")
  void summary_all_yoy() {
    when(businessRepository.findAll()).thenReturn(List.of(academy, publishing));
    stubScope(academy, 1L, 1_000_000L, 400_000L, 800_000L, 300_000L);
    stubScope(publishing, 2L, 500_000L, 100_000L, 600_000L, 200_000L);
    when(paymentRepository.findAll()).thenReturn(List.of());
    when(studentRepository.countByStatus(EasyStudentStatus.ENROLLED)).thenReturn(12L);

    EasyExecutiveSummaryResponse summary = service.summary(null);

    assertThat(summary.businessName()).isEqualTo("전체");
    assertThat(summary.currentIncome()).isEqualTo(1_500_000L);
    assertThat(summary.currentExpense()).isEqualTo(500_000L);
    assertThat(summary.currentProfit()).isEqualTo(1_000_000L);
    assertThat(summary.previousIncome()).isEqualTo(1_400_000L);
    assertThat(summary.incomeDiff()).isEqualTo(100_000L);
    assertThat(summary.incomeRate()).isNotNull();
    assertThat(summary.slices()).hasSize(2);
    assertThat(summary.briefing()).isNotEmpty();
    // 종소세는 계산하지 않는다 — placeholder 계약만
    assertThat(summary.incomeTaxPreview().status()).isEqualTo("PENDING_REAL_DATA");
    assertThat(summary.studentCount()).isEqualTo(12L);
  }

  @Test
  @DisplayName("사업체 선택 — 해당 사업체만 집계, 출판사 섞이지 않음")
  void summary_business_isolated() {
    when(businessRepository.findById(1L)).thenReturn(Optional.of(academy));
    stubScope(academy, 1L, 1_000_000L, 400_000L, 800_000L, 300_000L);
    when(paymentRepository.findByBusiness_BusinessId(1L)).thenReturn(List.of());

    EasyExecutiveSummaryResponse summary = service.summary(1L);

    assertThat(summary.businessName()).isEqualTo("학원");
    assertThat(summary.currentIncome()).isEqualTo(1_000_000L);
    assertThat(summary.slices()).hasSize(1);
    assertThat(summary.slices().get(0).businessName()).isEqualTo("학원");
  }

  @Test
  @DisplayName("작년 동기가 0원이면 증감률 null — 가짜 % 금지")
  void summary_noPrevious_rateNull() {
    when(businessRepository.findAll()).thenReturn(List.of(academy));
    LocalDate today = LocalDate.now();
    when(txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
        any(), any(), any())).thenReturn(List.of());
    when(paymentRepository.findAll()).thenReturn(List.of());
    when(studentRepository.countByStatus(any())).thenReturn(0L);

    EasyExecutiveSummaryResponse summary = service.summary(null);

    assertThat(summary.incomeRate()).isNull();
    assertThat(summary.expenseRate()).isNull();
    assertThat(summary.profitRate()).isNull();
    assertThat(summary.briefing()).anyMatch(line -> line.contains("비교 기준"));
  }
}
