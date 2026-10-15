package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;

public record EasyPaymentRequest(
    Long studentId,
    Long businessId,
    String targetMonth,
    Long billedAmount,
    Long paidAmount,
    LocalDate paidOn,
    String payMethod,
    String memo
) {
}
