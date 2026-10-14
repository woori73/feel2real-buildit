package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * KB raw row after type/format normalization only — no matching, no dedupe, no accounting
 * decision. Amount stays signed and no FX conversion is ever applied.
 *
 * <ul>
 *   <li>Domestic rows: {@code currency}=KRW, {@code eventAmount}=원 단위 exact long.</li>
 *   <li>Foreign rows: {@code currency}=USD, {@code eventAmount}=US-cent exact long (no FX rate
 *   invented — downstream {@code approveAndPost} rejects non-KRW explicitly, so a foreign row
 *   can never be wrongly posted; it waits for an explicit conversion flow).</li>
 * </ul>
 *
 * <p>{@code confirmationDate} follows the {@code EasyCardEvent} contract ("issuer's
 * confirm/settlement date, when the source distinguishes it"): 전표매입 rows carry 결제예정일,
 * 전표미매입 rows carry null. This is NOT an accounting-confirmation claim — the event still
 * enters as PENDING_REVIEW.
 *
 * <p>{@code occurrenceIndex} mirrors the Shinhan contract: 0-based position among rows that are
 * otherwise identical in this same parse, stable across re-imports of the same file.
 */
public record KbCardNormalizedTransaction(
    String source,
    String issuer,
    Long businessId,
    LocalDate usageDate,
    LocalDate confirmationDate,
    String cardName,
    String merchantName,
    long domesticAmountWon,
    BigDecimal foreignAmount,
    String currency,
    long eventAmount,
    String paymentMethod,
    String slipStatus,
    String approvalNumber,
    int occurrenceIndex,
    int sheetRowIndex
) {
  public boolean isNegative() {
    return eventAmount < 0;
  }

  public boolean isForeign() {
    return !"KRW".equals(currency);
  }
}
