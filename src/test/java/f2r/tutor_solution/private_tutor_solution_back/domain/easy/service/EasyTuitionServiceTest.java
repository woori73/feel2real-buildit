package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyPaymentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 수납 — 상태 자동 계산·월 형식·중복 방지 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyTuitionService 수납 테스트")
class EasyTuitionServiceTest {

  @Mock EasyTuitionPaymentRepository paymentRepository;
  @Mock EasyStudentRepository studentRepository;
  @Mock EasyBusinessRepository businessRepository;

  private EasyTuitionService service;
  private EasyStudent student;
  private EasyBusiness academy;

  @BeforeEach
  void setUp() {
    service = new EasyTuitionService(paymentRepository, studentRepository, businessRepository);
    student = EasyStudent.builder().name("김OO").school("압구정중").grade(2).build();
    academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
  }

  private EasyPaymentRequest request(long billed, long paid) {
    return new EasyPaymentRequest(1L, 2L, "2026-09", billed, paid, null, "계좌이체", null);
  }

  @Test
  @DisplayName("미납 등록 → 상태 UNPAID")
  void create_unpaid() {
    when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(academy));
    when(paymentRepository.existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(
        any(), any(), eq("2026-09")))
        .thenReturn(false);
    when(paymentRepository.save(any(EasyTuitionPayment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EasyPaymentResponse response = service.create(request(300000L, 0L));

    assertThat(response.status()).isEqualTo("UNPAID");
    assertThat(response.balance()).isEqualTo(300000L);
  }

  @Test
  @DisplayName("일부납 → PARTIAL, 완납 → PAID")
  void partial_and_paid() {
    when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(academy));
    when(paymentRepository.existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(
        any(), any(), eq("2026-09")))
        .thenReturn(false);
    when(paymentRepository.save(any(EasyTuitionPayment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.create(request(300000L, 100000L)).status()).isEqualTo("PARTIAL");
    assertThat(service.create(request(300000L, 300000L)).status()).isEqualTo("PAID");
  }

  @Test
  @DisplayName("잘못된 월 형식·음수·중복 거부")
  void invalid_rejected() {
    when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(academy));

    assertThatThrownBy(() -> service.create(
        new EasyPaymentRequest(1L, 2L, "2026-13", 300000L, 0L, null, "", null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.create(
        new EasyPaymentRequest(1L, 2L, "2026-09", -1L, 0L, null, "", null)))
        .isInstanceOf(IllegalArgumentException.class);

    when(paymentRepository.existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(
        any(), any(), eq("2026-09")))
        .thenReturn(true);
    assertThatThrownBy(() -> service.create(request(300000L, 0L)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("수정·삭제 + 목록 상태 필터")
  void update_delete_listFilter() {
    EasyTuitionPayment stored = EasyTuitionPayment.builder()
        .student(student).business(academy).targetMonth("2026-09")
        .billedAmount(300000L).paidAmount(0L).build();
    when(paymentRepository.findById(5L)).thenReturn(Optional.of(stored));
    when(businessRepository.findById(2L)).thenReturn(Optional.of(academy));

    EasyPaymentResponse updated = service.update(5L, request(300000L, 300000L));
    assertThat(updated.status()).isEqualTo("PAID");

    service.delete(5L);
    verify(paymentRepository).delete(stored);

    when(paymentRepository.findByStudent_StudentIdOrderByTargetMonthDesc(1L))
        .thenReturn(List.of(stored));
    List<EasyPaymentResponse> paidOnly = service.list(1L, null, EasyPaymentStatus.PAID);
    assertThat(paidOnly).hasSize(1);
    List<EasyPaymentResponse> unpaidOnly = service.list(1L, null, EasyPaymentStatus.UNPAID);
    assertThat(unpaidOnly).isEmpty();
  }
}
