package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;

public record EasyCollectorTaskStartRequest(
    Long businessId,
    Long collectorId,
    String sourceCode,
    String dataType,
    LocalDate requestedFrom,
    LocalDate requestedTo
) {
}
