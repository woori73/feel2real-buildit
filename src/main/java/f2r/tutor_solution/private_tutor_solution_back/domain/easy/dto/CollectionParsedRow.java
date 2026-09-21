package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * 파일 어댑터가 산출한 정규화 전 레코드. error가 있으면 필수 필드를 채우지 못한 원본 행이다
 * (원본은 raw로 보존하고 import에는 넘기지 않는다).
 */
public record CollectionParsedRow(
    int rowNo,
    Map<String, String> originalRow,
    String rawJson,
    LocalDate occurredOn,
    String direction,
    Long amount,
    String counterparty,
    String description,
    String payMethod,
    Long supplyAmount,
    Long vatAmount,
    String evidenceType,
    String externalAccount,
    String externalId,
    String error
) {
  public boolean isValid() {
    return error == null;
  }
}
