package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

/**
 * 결제 대행사 호출부. 카드 정보는 대행사 결제창이 직접 받고, F2R 서버는 주문번호·금액·결제 식별값만 다룬다.
 * 기본 구현은 돈이 움직이지 않는 {@link FakeTuitionPaymentGateway}다. 실제 대행사는 계약·대표 승인 뒤 별도 구현으로 붙인다.
 */
public interface TuitionPaymentGateway {

  /** 주문에 기록할 구현 코드. */
  String code();

  /** 결제창으로 보낼 주소. */
  String checkoutPath(String token, String orderId);

  /** 대행사에 최종 승인을 요청한다. amount는 서버가 정한 주문 금액이다. */
  Approval approve(String orderId, String paymentKey, long amount);

  record Approval(boolean approved, String failureCode) {}
}
