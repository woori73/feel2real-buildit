package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyPaymentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 월 수납 CRUD — 상태(미납/일부납/완납)는 청구/납부 기준 자동 계산이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyTuitionService {

  private static final Pattern MONTH_PATTERN =
      Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

  private final EasyTuitionPaymentRepository paymentRepository;
  private final EasyStudentRepository studentRepository;
  private final EasyBusinessRepository businessRepository;

  /** 수납 목록 — 학생/대상월/상태 필터. null은 전체. */
  public List<EasyPaymentResponse> list(Long studentId, String targetMonth, EasyPaymentStatus status) {
    List<EasyTuitionPayment> payments = studentId == null
        ? paymentRepository.findAll()
        : paymentRepository.findByStudent_StudentIdOrderByTargetMonthDesc(studentId);
    List<EasyPaymentResponse> result = new ArrayList<>();
    for (EasyTuitionPayment payment : payments) {
      if (hasText(targetMonth) && !targetMonth.equals(payment.getTargetMonth())) {
        continue;
      }
      if (status != null && payment.getStatus() != status) {
        continue;
      }
      result.add(EasyPaymentResponse.from(payment));
    }
    return result;
  }

  @Transactional
  public EasyPaymentResponse create(EasyPaymentRequest request) {
    EasyStudent student = requireStudent(request.studentId());
    EasyBusiness business = requireActiveBusiness(request.businessId());
    String month = requireMonth(request.targetMonth());
    if (paymentRepository.existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(
        student.getStudentId(), business.getBusinessId(), month)) {
      throw new IllegalArgumentException("이미 등록된 수납입니다: " + month);
    }
    EasyTuitionPayment payment = EasyTuitionPayment.builder()
        .student(student)
        .business(business)
        .targetMonth(month)
        .billedAmount(requireNonNegative(request.billedAmount(), "청구 금액"))
        .paidAmount(requireNonNegative(request.paidAmount() == null ? 0L : request.paidAmount(), "납부 금액"))
        .paidOn(request.paidOn())
        .payMethod(request.payMethod())
        .memo(request.memo())
        .build();
    return EasyPaymentResponse.from(paymentRepository.save(payment));
  }

  @Transactional
  public EasyPaymentResponse update(Long paymentId, EasyPaymentRequest request) {
    EasyTuitionPayment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new IllegalArgumentException("수납을 찾을 수 없습니다: paymentId=" + paymentId));
    EasyBusiness business = requireActiveBusiness(request.businessId());
    String month = requireMonth(request.targetMonth());
    if (!month.equals(payment.getTargetMonth())
        && paymentRepository.existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(
            payment.getStudent().getStudentId(), business.getBusinessId(), month)) {
      throw new IllegalArgumentException("이미 등록된 수납입니다: " + month);
    }
    payment.update(
        business,
        month,
        requireNonNegative(request.billedAmount(), "청구 금액"),
        requireNonNegative(request.paidAmount() == null ? 0L : request.paidAmount(), "납부 금액"),
        request.paidOn(),
        request.payMethod(),
        request.memo());
    return EasyPaymentResponse.from(payment);
  }

  @Transactional
  public void delete(Long paymentId) {
    EasyTuitionPayment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new IllegalArgumentException("수납을 찾을 수 없습니다: paymentId=" + paymentId));
    paymentRepository.delete(payment);
  }

  private EasyStudent requireStudent(Long studentId) {
    if (studentId == null) {
      throw new IllegalArgumentException("학생을 선택해 주세요.");
    }
    return studentRepository.findById(studentId)
        .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다: " + studentId));
  }

  private EasyBusiness requireActiveBusiness(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다: " + businessId));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체에는 등록할 수 없습니다.");
    }
    return business;
  }

  private String requireMonth(String targetMonth) {
    if (targetMonth == null || !MONTH_PATTERN.matcher(targetMonth.trim()).matches()) {
      throw new IllegalArgumentException("대상 월은 YYYY-MM 형식이어야 합니다.");
    }
    return targetMonth.trim();
  }

  private long requireNonNegative(Long amount, String label) {
    if (amount == null || amount < 0) {
      throw new IllegalArgumentException(label + "은 0 이상이어야 합니다.");
    }
    return amount;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
