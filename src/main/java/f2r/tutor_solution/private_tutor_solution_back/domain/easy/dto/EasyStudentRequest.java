package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;

public record EasyStudentRequest(
    String name,
    String school,
    Integer grade,
    String phone,
    String parentPhone,
    String status,
    LocalDate enrolledOn,
    String memo
) {
}
