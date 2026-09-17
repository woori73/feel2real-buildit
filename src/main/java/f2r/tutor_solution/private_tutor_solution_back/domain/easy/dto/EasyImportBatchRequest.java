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
      String rawData,
      String externalAccount
  ) {
    /** V974 호환 — 원본 계정명 없이 호출할 때 사용. */
    public EasyImportRowInput(
        String occurredOn,
        String direction,
        Long amount,
        String counterparty,
        String description,
        String payMethod,
        Long supplyAmount,
        Long vatAmount,
        String evidenceType,
        String rawData) {
      this(occurredOn, direction, amount, counterparty, description, payMethod,
          supplyAmount, vatAmount, evidenceType, rawData, null);
    }
  }
}
