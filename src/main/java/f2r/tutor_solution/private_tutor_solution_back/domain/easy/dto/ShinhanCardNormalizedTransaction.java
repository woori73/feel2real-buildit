package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;

/**
 * Shinhan raw row after type/format normalization only — no matching, no dedupe, no accounting
 * decision. Amount stays signed (KRW, no fractional won). {@code occurrenceIndex} is this row's
 * 0-based position among rows that are otherwise byte-identical in this same parse (same
 * transactionDate/confirmationDate/merchant/productType/amount) — it exists only to keep
 * genuinely repeated identical transactions (e.g. four separate 교원라이프 59,900원 charges on the
 * same day) from collapsing into one event under content-based dedupe, while staying stable
 * across re-imports of the same file (same row order -> same index).
 */
public record ShinhanCardNormalizedTransaction(
    String source,
    String issuer,
    Long businessId,
    LocalDate transactionDate,
    LocalDate confirmationDate,
    String cardHolderType,
    String maskedCard,
    String productType,
    String merchantName,
    long amount,
    Long supplyAmount,
    Long vatAmount,
    Long taxExemptAmount,
    String merchantRegistrationNumber,
    int occurrenceIndex,
    int sheetRowIndex
) {
  public boolean isNegative() {
    return amount < 0;
  }
}
