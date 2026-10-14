package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.KbCardNormalizedTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.KbCardRawRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure type/format normalization — no matching, no dedupe, no business/accounting decision.
 *
 * <p>Confirmation-date mapping (verified against the domain contract, not assumed): {@code
 * EasyCardEvent.confirmationDate} is the "issuer's confirm/settlement date, when the source
 * distinguishes it". KB distinguishes exactly this via slip status — 전표매입 rows carry a
 * 결제예정일, 전표미매입 rows carry none. So 전표매입 → 결제예정일 (required: a purchased slip
 * without a scheduled date is malformed and fails closed), 전표미매입 → null. This says nothing
 * about accounting confirmation — every row still enters as PENDING_REVIEW.
 *
 * <p>Foreign rows keep their USD amount as exact US cents with currency USD. No FX rate is ever
 * invented; downstream {@code approveAndPost} explicitly rejects non-KRW, so a foreign row can
 * sit in review but never be wrongly posted.
 */
@Component
public class KbCardNormalizer {

  public static final String SOURCE = "KB_INDIVIDUAL_BUSINESS_USAGE_XLS";
  public static final String ISSUER = "KB국민카드";

  public List<KbCardNormalizedTransaction> normalize(List<KbCardRawRow> rows, Long businessId) {
    List<KbCardNormalizedTransaction> result = new ArrayList<>();
    Map<String, Integer> occurrenceCounters = new HashMap<>();
    for (KbCardRawRow row : rows) {
      boolean foreign = row.foreignAmount() != null
          && row.foreignAmount().compareTo(BigDecimal.ZERO) != 0;
      String currency;
      long eventAmount;
      if (foreign) {
        currency = "USD";
        eventAmount = toCentsExact(row.foreignAmount(), row.sheetRowIndex());
      } else {
        currency = "KRW";
        eventAmount = requireExactLong(row.domesticAmount(), row.sheetRowIndex(), "국내이용금액");
        if (eventAmount == 0) {
          throw new KbCardExcelParser.KbCardParseException(
              "행 " + row.sheetRowIndex() + ": 국내·해외 금액이 모두 0입니다.");
        }
      }
      LocalDate confirmationDate = resolveConfirmationDate(row);
      String groupKey = String.join("|",
          row.usageDate().toString(), row.cardName(), row.merchantName(),
          row.paymentMethod(), currency, String.valueOf(eventAmount));
      int occurrenceIndex = occurrenceCounters.merge(groupKey, 1, Integer::sum) - 1;
      result.add(new KbCardNormalizedTransaction(
          SOURCE,
          ISSUER,
          businessId,
          row.usageDate(),
          confirmationDate,
          row.cardName(),
          row.merchantName(),
          foreign ? 0L : eventAmount,
          foreign ? row.foreignAmount() : null,
          currency,
          eventAmount,
          row.paymentMethod(),
          row.slipStatus(),
          row.approvalNumber(),
          occurrenceIndex,
          row.sheetRowIndex()));
    }
    return result;
  }

  private LocalDate resolveConfirmationDate(KbCardRawRow row) {
    if (KbCardExcelParser.SLIP_PURCHASED.equals(row.slipStatus())) {
      if (row.expectedPaymentDate() == null) {
        throw new KbCardExcelParser.KbCardParseException(
            "행 " + row.sheetRowIndex() + ": 전표매입인데 결제예정일이 없습니다.");
      }
      return row.expectedPaymentDate();
    }
    return null;
  }

  private long requireExactLong(BigDecimal value, int rowIndex, String column) {
    if (value == null) {
      throw new KbCardExcelParser.KbCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    try {
      return value.longValueExact();
    } catch (ArithmeticException e) {
      throw new KbCardExcelParser.KbCardParseException(
          "행 " + rowIndex + ": " + column + "이 정수(원 단위)가 아닙니다.");
    }
  }

  private long toCentsExact(BigDecimal dollars, int rowIndex) {
    try {
      return dollars.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    } catch (ArithmeticException e) {
      throw new KbCardExcelParser.KbCardParseException(
          "행 " + rowIndex + ": 해외이용금액이 소수 둘째 자리 형식이 아닙니다.");
    }
  }
}
