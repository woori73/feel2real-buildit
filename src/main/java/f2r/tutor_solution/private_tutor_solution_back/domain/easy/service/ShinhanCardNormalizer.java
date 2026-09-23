package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.ShinhanCardNormalizedTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.ShinhanCardRawRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Pure type/format normalization — no matching, no dedupe, no business/accounting decision. */
@Component
public class ShinhanCardNormalizer {

  public static final String SOURCE = "SHINHAN_INDIVIDUAL_BUSINESS_USAGE_XLS";

  public List<ShinhanCardNormalizedTransaction> normalize(List<ShinhanCardRawRow> rows, Long businessId) {
    List<ShinhanCardNormalizedTransaction> result = new ArrayList<>();
    Map<String, Integer> occurrenceCounters = new HashMap<>();
    for (ShinhanCardRawRow row : rows) {
      long amount = requireExactLong(row.amount(), row.sheetRowIndex(), "이용금액");
      String groupKey = String.join("|",
          row.transactionDate().toString(), row.confirmationDate().toString(),
          row.merchantName(), row.productType(), String.valueOf(amount));
      int occurrenceIndex = occurrenceCounters.merge(groupKey, 1, Integer::sum) - 1;
      result.add(new ShinhanCardNormalizedTransaction(
          SOURCE,
          "신한카드",
          businessId,
          row.transactionDate(),
          row.confirmationDate(),
          row.cardHolderType(),
          row.maskedCard(),
          row.productType(),
          row.merchantName(),
          amount,
          toLongOrNull(row.supplyAmount()),
          toLongOrNull(row.vatAmount()),
          toLongOrNull(row.taxExemptAmount()),
          row.merchantRegistrationNumber(),
          occurrenceIndex,
          row.sheetRowIndex()));
    }
    return result;
  }

  private long requireExactLong(BigDecimal value, int rowIndex, String column) {
    try {
      return value.longValueExact();
    } catch (ArithmeticException e) {
      throw new ShinhanCardExcelParser.ShinhanCardParseException(
          "행 " + rowIndex + ": " + column + "이 정수(원 단위)가 아닙니다: " + value);
    }
  }

  private Long toLongOrNull(BigDecimal value) {
    if (value == null) return null;
    return value.longValueExact();
  }
}
