package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of Shinhan "개인사업자 이용내역" (BIFF8 .xls), as parsed — no interpretation yet.
 * Column order matches the real source exactly:
 * 거래일 | 확정일 | 카드구분 | 이용카드(뒤4자리) | 상품구분 | 가맹점명 |
 * 이용금액 | 공급가액 | 부가세 | 비과세금액 | 사업자등록번호
 */
public record ShinhanCardRawRow(
    LocalDate transactionDate,
    LocalDate confirmationDate,
    String cardHolderType,
    String maskedCard,
    String productType,
    String merchantName,
    BigDecimal amount,
    BigDecimal supplyAmount,
    BigDecimal vatAmount,
    BigDecimal taxExemptAmount,
    String merchantRegistrationNumber,
    int sheetRowIndex
) {
}
