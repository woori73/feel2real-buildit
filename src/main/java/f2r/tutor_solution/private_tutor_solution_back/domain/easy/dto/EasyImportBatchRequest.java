package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

public record EasyImportBatchRequest(
    Long businessId,
    String sourceName,
    String sourceKind,
    List<EasyImportRowInput> rows
) {
  public record EasyImportRowInput(
      String occurredOn,
      String direction,
      Long amount,
      String counterparty,
      String description,
      String payMethod,
      Long supplyAmount,
      Long vatAmount,
      String evidenceType,
      String rawData
  ) {
  }
}
