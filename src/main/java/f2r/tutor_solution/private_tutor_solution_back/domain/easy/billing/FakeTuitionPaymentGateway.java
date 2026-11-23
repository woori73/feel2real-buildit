package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import org.springframework.stereotype.Component;

/** 시험용 결제 대행 구현 — 외부 호출이 없고 돈이 움직이지 않는다. 결제 식별값이 test_ok_로 시작하면 승인으로 본다. */
@Component
public class FakeTuitionPaymentGateway implements TuitionPaymentGateway {

  public static final String CODE = "FAKE";
  public static final String OK_PREFIX = "test_ok_";

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public String checkoutPath(String token, String orderId) {
    return "/pay/" + token + "/test-checkout?orderId=" + orderId;
  }

  @Override
  public Approval approve(String orderId, String paymentKey, long amount) {
    if (paymentKey != null && paymentKey.startsWith(OK_PREFIX)) {
      return new Approval(true, null);
    }
    return new Approval(false, "TEST_DECLINED");
  }
}
