package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import java.time.LocalDate;

public record EasyPaymentResponse(
    Long paymentId,
    Long studentId,
    String studentName,
    Long businessId,
    String businessName,
    String targetMonth,
    long billedAmount,
    long paidAmount,
    LocalDate paidOn,
    String payMethod,
    String memo,
    String status,
    long balance
) {
  public static EasyPaymentResponse from(EasyTuitionPayment payment) {
    return new EasyPaymentResponse(
        payment.getPaymentId(),
        payment.getStudent().getStudentId(),
        payment.getStudent().getName(),
        payment.getBusiness().getBusinessId(),
        payment.getBusiness().getName(),
        payment.getTargetMonth(),
        payment.getBilledAmount(),
        payment.getPaidAmount(),
        payment.getPaidOn(),
        payment.getPayMethod(),
        payment.getMemo(),
        payment.getStatus().name(),
        payment.getBalance());
  }
}
