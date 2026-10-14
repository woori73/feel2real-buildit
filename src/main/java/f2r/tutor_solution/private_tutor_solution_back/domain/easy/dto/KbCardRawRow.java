package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of KB "개인사업자용 이용내역" (BIFF8 .xls), as parsed — no interpretation yet.
 * Column order matches the real source exactly (verified against REAL_SOURCE_SAMPLE_KB_001,
 * 2026-09-01~09-24, 50 data rows):
 * 이용일 | 이용시간 | 이용고객명 | 이용카드명 | 이용하신곳 | 국내이용금액(원) | 해외이용금액($) |
 * 결제방법 | 가맹점정보 | 할인금액 | 적립(예상)포인트리 | 상태 | 결제예정일 | 승인번호
 *
 * <p>Real status values observed: 전표매입 / 전표미매입. No cancellation rows existed in the
 * verified file, so cancellation representation stays UNKNOWN — never guessed here.
 */
public record KbCardRawRow(
    LocalDate usageDate,
    String usageTime,
    String customerName,
    String cardName,
    String merchantName,
    BigDecimal domesticAmount,
    BigDecimal foreignAmount,
    String paymentMethod,
    String merchantInfo,
    BigDecimal discountAmount,
    BigDecimal rewardPoints,
    String slipStatus,
    LocalDate expectedPaymentDate,
    String approvalNumber,
    int sheetRowIndex
) {
}
