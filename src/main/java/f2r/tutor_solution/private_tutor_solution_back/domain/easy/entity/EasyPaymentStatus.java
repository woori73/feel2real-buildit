package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Easy 수납 상태 — DB에 저장하지 않고 청구/납부 금액으로 자동 계산한다.
 * 미납(0원 납부 또는 미등록) / 일부납 / 완납(납부 >= 청구).
 */
public enum EasyPaymentStatus {
  UNPAID,
  PARTIAL,
  PAID;

  public static EasyPaymentStatus of(long billedAmount, long paidAmount) {
    if (paidAmount <= 0) {
      return UNPAID;
    }
    if (paidAmount >= billedAmount) {
      return PAID;
    }
    return PARTIAL;
  }
}
