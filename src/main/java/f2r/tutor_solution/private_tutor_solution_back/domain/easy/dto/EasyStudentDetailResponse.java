package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

public record EasyStudentDetailResponse(
    EasyStudentResponse student,
    List<EasyPaymentResponse> payments
) {
}
