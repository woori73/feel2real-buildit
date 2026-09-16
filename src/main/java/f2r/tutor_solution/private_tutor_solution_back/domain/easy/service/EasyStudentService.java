package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 학생 원장 CRUD + 상세(수납 합계 포함).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyStudentService {

  private final EasyStudentRepository studentRepository;
  private final EasyTuitionPaymentRepository paymentRepository;

  public List<EasyStudentResponse> list() {
    return studentRepository.findAllByOrderByNameAsc().stream()
        .map(student -> withTotals(student))
        .toList();
  }

  public EasyStudentDetailResponse detail(Long studentId) {
    EasyStudent student = requireStudent(studentId);
    List<EasyPaymentResponse> payments = paymentRepository
        .findByStudent_StudentIdOrderByTargetMonthDesc(studentId).stream()
        .map(EasyPaymentResponse::from)
        .toList();
    return new EasyStudentDetailResponse(withTotals(student), payments);
  }

  @Transactional
  public EasyStudentResponse create(EasyStudentRequest request) {
    EasyStudent student = EasyStudent.builder()
        .name(requireName(request.name()))
        .school(request.school())
        .grade(request.grade() == null ? 0 : request.grade())
        .phone(request.phone())
        .parentPhone(request.parentPhone())
        .status(parseStatus(request.status()))
        .enrolledOn(request.enrolledOn())
        .memo(request.memo())
        .build();
    return withTotals(studentRepository.save(student));
  }

  @Transactional
  public EasyStudentResponse update(Long studentId, EasyStudentRequest request) {
    EasyStudent student = requireStudent(studentId);
    student.update(
        requireName(request.name()),
        request.school(),
        request.grade() == null ? 0 : request.grade(),
        request.phone(),
        request.parentPhone(),
        parseStatus(request.status()),
        request.enrolledOn(),
        request.memo());
    return withTotals(student);
  }

  private EasyStudent requireStudent(Long studentId) {
    return studentRepository.findById(studentId)
        .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다: studentId=" + studentId));
  }

  private EasyStudentResponse withTotals(EasyStudent student) {
    long billed = 0L;
    long paid = 0L;
    List<EasyTuitionPayment> payments = paymentRepository
        .findByStudent_StudentIdOrderByTargetMonthDesc(student.getStudentId());
    for (EasyTuitionPayment payment : payments) {
      billed += payment.getBilledAmount();
      paid += payment.getPaidAmount();
    }
    return EasyStudentResponse.of(student, billed, paid);
  }

  private String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("학생 이름을 입력해 주세요.");
    }
    return name.trim();
  }

  private EasyStudentStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return EasyStudentStatus.ENROLLED;
    }
    try {
      return EasyStudentStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("재원 상태 값이 올바르지 않습니다.");
    }
  }
}
