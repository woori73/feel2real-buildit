package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;

public record EasyTxnRequest(
    Long businessId,
    LocalDate occurredOn,
    String direction,
    Long amount,
    String category,
    String counterparty,
    String description,
    String payMethod,
    String memo,
    String receiptRef
) {
}
