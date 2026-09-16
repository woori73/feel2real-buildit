package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
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

/**
 * 학생 원장 — 등록/수정/상세 합계 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyStudentService 원장 테스트")
class EasyStudentServiceTest {

  @Mock EasyStudentRepository studentRepository;
  @Mock EasyTuitionPaymentRepository paymentRepository;

  private EasyStudentService service;

  @BeforeEach
  void setUp() {
    service = new EasyStudentService(studentRepository, paymentRepository);
  }

  @Test
  @DisplayName("학생 등록 성공 + 이름 누락 거부")
  void create_ok_blankName_rejected() {
    when(studentRepository.save(any(EasyStudent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(paymentRepository.findByStudent_StudentIdOrderByTargetMonthDesc(null))
        .thenReturn(List.of());

    var response = service.create(new EasyStudentRequest(
        "김OO", "압구정중", (byte) 2, "010-0000-0000", "010-1111-2222",
        "ENROLLED", LocalDate.of(2026, 3, 2), null));

    assertThat(response.name()).isEqualTo("김OO");
    assertThat(response.status()).isEqualTo("ENROLLED");

    assertThatThrownBy(() -> service.create(new EasyStudentRequest(
        "  ", "압구정중", (byte) 2, null, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("학생 수정 + 상세 청구/납부/미수 합계")
  void update_and_detailTotals() {
    EasyStudent student = EasyStudent.builder().name("김OO").school("압구정중").grade((byte) 2).build();
    EasyBusiness academy = EasyBusiness.builder().code("ACADEMY").name("학원").active(true).sortOrder(1).build();
    List<EasyTuitionPayment> payments = List.of(
        EasyTuitionPayment.builder().student(student).business(academy)
            .targetMonth("2026-09").billedAmount(300000L).paidAmount(200000L).build());
    when(studentRepository.findById(3L)).thenReturn(Optional.of(student));
    // 목이 반환하는 엔티티는 영속 ID가 없어 withTotals 조회가 null 키로 들어간다 — 둘 다 스텁한다.
    when(paymentRepository.findByStudent_StudentIdOrderByTargetMonthDesc(3L)).thenReturn(payments);
    when(paymentRepository.findByStudent_StudentIdOrderByTargetMonthDesc(null)).thenReturn(payments);

    var updated = service.update(3L, new EasyStudentRequest(
        "김OO", "신사중", (byte) 3, null, null, "ENROLLED", null, "전학"));
    assertThat(updated.school()).isEqualTo("신사중");

    EasyStudentDetailResponse detail = service.detail(3L);
    assertThat(detail.student().billedTotal()).isEqualTo(300000L);
    assertThat(detail.student().paidTotal()).isEqualTo(200000L);
    assertThat(detail.student().balance()).isEqualTo(100000L);
    assertThat(detail.payments()).hasSize(1);
    assertThat(detail.payments().get(0).status()).isEqualTo("PARTIAL");
    assertThat(detail.businesses()).containsExactly("학원");
  }
}
