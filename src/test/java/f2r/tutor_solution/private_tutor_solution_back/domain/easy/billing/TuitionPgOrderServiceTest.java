package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.NOW;
import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.link;
import static f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillingTestFixtures.payment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionPgOrderService.Outcome;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TuitionPgOrderServiceTest {

  private final TuitionBillLinkService linkService = mock(TuitionBillLinkService.class);
  private final EasyTuitionPgOrderRepository orders = mock(EasyTuitionPgOrderRepository.class);
  private final EasyTuitionPaymentRepository payments = mock(EasyTuitionPaymentRepository.class);
  private final TuitionPgOrderService service = new TuitionPgOrderService(
      linkService, orders, payments, new FakeTuitionPaymentGateway());

  private final String token = TuitionBillTokens.newToken();
  private EasyTuitionPayment payment;
  private EasyTuitionBillLink link;
  private EasyTuitionPgOrder order;

  @BeforeEach
  void setUp() {
    payment = payment(1L, "가나다", 300_000, 0);
    link = link(1L, payment, token, "4821");
    order = EasyTuitionPgOrder.builder().orderId("order1").link(link).payment(payment)
        .amount(300_000).gateway("FAKE").build();
    when(linkService.findOpen(token, NOW)).thenReturn(Optional.of(link));
    when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orders.findLockedByOrderId("order1")).thenReturn(Optional.of(order));
    when(orders.findByOrderId("order1")).thenReturn(Optional.of(order));
    when(payments.findLockedByPaymentId(1L)).thenReturn(Optional.of(payment));
  }

  @Test
  @DisplayName("given 열린 청구 링크 when 주문 만들기 then 금액은 서버의 남은 청구액이다")
  void createOrderUsesServerAmount() {
    // when
    TuitionPgOrderService.Order created = service.createOrder(token, NOW).orElseThrow();
    // then
    assertThat(created.amount()).isEqualTo(300_000);
    assertThat(created.orderId()).matches("[0-9a-f]{32}");
    assertThat(created.checkoutPath()).startsWith("/pay/" + token + "/test-checkout?orderId=");
  }

  @Test
  @DisplayName("given 닫힌 링크·완납 수납 when 주문 만들기 then 만들지 않는다")
  void createOrderRejectsClosedOrPaid() {
    // given
    String other = TuitionBillTokens.newToken();
    when(linkService.findOpen(other, NOW)).thenReturn(Optional.empty());
    // when / then
    assertThat(service.createOrder(other, NOW)).isEmpty();
    payment.applyDeposit(300_000, NOW.toLocalDate(), "계좌이체");
    assertThat(service.createOrder(token, NOW)).isEmpty();
  }

  @Test
  @DisplayName("given 준비 주문 when 금액이 맞는 결제 확인 then 납부 기록 1회, 같은 확인이 또 와도 다시 기록하지 않는다")
  void confirmOnce() {
    // when
    Outcome first = service.confirm(token, "order1", "test_ok_order1", 300_000L, NOW);
    Outcome second = service.confirm(token, "order1", "test_ok_order1", 300_000L, NOW);
    Outcome viaNotify = service.confirm(null, "order1", "test_ok_order1", 300_000L, NOW);
    // then
    assertThat(first).isEqualTo(Outcome.PAID);
    assertThat(second).isEqualTo(Outcome.ALREADY_PAID);
    assertThat(viaNotify).isEqualTo(Outcome.ALREADY_PAID);
    assertThat(payment.getPaidAmount()).isEqualTo(300_000);
    assertThat(payment.getPayMethod()).isEqualTo("카드(시험)");
    assertThat(order.getStatus()).isEqualTo(EasyTuitionPgOrder.Status.PAID);
  }

  @Test
  @DisplayName("given 준비 주문 when 브라우저가 보낸 금액이 주문과 다름 then 승인하지 않고 납부 기록도 없다")
  void confirmRejectsTamperedAmount() {
    // when
    Outcome outcome = service.confirm(token, "order1", "test_ok_order1", 1_000L, NOW);
    // then
    assertThat(outcome).isEqualTo(Outcome.AMOUNT_MISMATCH);
    assertThat(payment.getPaidAmount()).isZero();
    assertThat(order.getStatus()).isEqualTo(EasyTuitionPgOrder.Status.FAILED);
  }

  @Test
  @DisplayName("given 주문 뒤 청구가 바뀜 when 결제 확인 then BILL_CHANGED, 납부 기록 없음")
  void confirmRejectsWhenBillChanged() {
    // given: 주문 뒤 계좌 입금으로 일부 납부됨
    payment.applyDeposit(100_000, NOW.toLocalDate(), "계좌이체");
    // when
    Outcome outcome = service.confirm(token, "order1", "test_ok_order1", 300_000L, NOW);
    // then
    assertThat(outcome).isEqualTo(Outcome.BILL_CHANGED);
    assertThat(payment.getPaidAmount()).isEqualTo(100_000);
  }

  @Test
  @DisplayName("given 대행사가 거절·다른 링크의 토큰·없는 주문 when 결제 확인 then 납부 기록 없음")
  void confirmDeclinedOrForeign() {
    // when / then
    assertThat(service.confirm(TuitionBillTokens.newToken(), "order1", "test_ok_order1",
        300_000L, NOW)).isEqualTo(Outcome.NOT_FOUND);
    assertThat(service.confirm(token, "nope", "test_ok_x", 300_000L, NOW))
        .isEqualTo(Outcome.NOT_FOUND);
    assertThat(service.confirm(token, "order1", null, 300_000L, NOW)).isEqualTo(Outcome.NOT_FOUND);
    assertThat(service.confirm(token, "order1", "declined", 300_000L, NOW))
        .isEqualTo(Outcome.DECLINED);
    assertThat(service.confirm(token, "order1", "test_ok_order1", 300_000L, NOW))
        .isEqualTo(Outcome.DECLINED);
    assertThat(payment.getPaidAmount()).isZero();
  }

  @Test
  @DisplayName("given 준비 주문 when 시험 결제창 조회 then 같은 링크의 준비 주문만 보인다")
  void findReadyOrder() {
    // when / then
    assertThat(service.findReadyOrder(token, "order1", NOW)).isPresent();
    assertThat(service.findReadyOrder(token, null, NOW)).isEmpty();
    order.markFailed();
    assertThat(service.findReadyOrder(token, "order1", NOW)).isEmpty();
  }
}
