package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제창 흐름의 뼈대(시험 모드). 주문 금액은 서버의 청구 잔액으로만 정하고, 브라우저·알림이 보낸 금액은
 * 주문 금액과 같은지 확인하는 데만 쓴다. 같은 결제 확인이 여러 번 와도 납부 기록은 한 번만 한다.
 */
@Service
@RequiredArgsConstructor
public class TuitionPgOrderService {

  static final String PAY_METHOD = "카드(시험)";

  public enum Outcome { PAID, ALREADY_PAID, AMOUNT_MISMATCH, BILL_CHANGED, DECLINED, NOT_FOUND }

  public record Order(String orderId, long amount, String checkoutPath) {}

  private final TuitionBillLinkService linkService;
  private final EasyTuitionPgOrderRepository orderRepository;
  private final EasyTuitionPaymentRepository paymentRepository;
  private final TuitionPaymentGateway gateway;

  /** 열려 있는 청구 링크에서 주문을 만든다. 금액은 요청에서 받지 않는다. */
  @Transactional
  public Optional<Order> createOrder(String token, LocalDateTime now) {
    Optional<EasyTuitionBillLink> found = linkService.findOpen(token, now);
    if (found.isEmpty() || found.get().getPayment().getBalance() <= 0) {
      return Optional.empty();
    }
    EasyTuitionBillLink link = found.get();
    EasyTuitionPgOrder order = orderRepository.save(EasyTuitionPgOrder.builder()
        .orderId(TuitionBillTokens.newOrderId())
        .link(link)
        .payment(link.getPayment())
        .amount(link.getPayment().getBalance())
        .gateway(gateway.code())
        .build());
    return Optional.of(new Order(order.getOrderId(), order.getAmount(),
        gateway.checkoutPath(token, order.getOrderId())));
  }

  /** 이 토큰의 링크에서 만든, 아직 결제 전인 주문만 돌려준다(시험 결제창 표시용). */
  @Transactional(readOnly = true)
  public Optional<Order> findReadyOrder(String token, String orderId, LocalDateTime now) {
    Optional<EasyTuitionBillLink> link = linkService.findOpen(token, now);
    if (link.isEmpty() || orderId == null) {
      return Optional.empty();
    }
    return orderRepository.findByOrderId(orderId)
        .filter(o -> o.getLink().getLinkId().equals(link.get().getLinkId()))
        .filter(o -> o.getStatus() == EasyTuitionPgOrder.Status.READY)
        .map(o -> new Order(o.getOrderId(), o.getAmount(), null));
  }

  /**
   * 결제 확인(성공 화면 복귀·대행사 알림 공용). token이 있으면 그 링크의 주문인지도 본다.
   * 이미 PAID인 주문이면 아무것도 바꾸지 않고 ALREADY_PAID를 돌려준다.
   */
  @Transactional
  public Outcome confirm(String token, String orderId, String paymentKey, Long claimedAmount,
      LocalDateTime now) {
    if (orderId == null || paymentKey == null || paymentKey.isBlank()
        || paymentKey.length() > 200 || claimedAmount == null) {
      return Outcome.NOT_FOUND;
    }
    EasyTuitionPgOrder order = orderRepository.findLockedByOrderId(orderId).orElse(null);
    if (order == null) {
      return Outcome.NOT_FOUND;
    }
    if (token != null) {
      String hash = TuitionBillTokens.hash(token);
      if (hash == null || !hash.equals(order.getLink().getTokenHash())) {
        return Outcome.NOT_FOUND;
      }
    }
    if (order.getStatus() == EasyTuitionPgOrder.Status.PAID) {
      return Outcome.ALREADY_PAID;
    }
    if (order.getStatus() == EasyTuitionPgOrder.Status.FAILED) {
      return Outcome.DECLINED;
    }
    if (claimedAmount != order.getAmount()) {
      order.markFailed();
      return Outcome.AMOUNT_MISMATCH;
    }
    EasyTuitionPayment payment = paymentRepository
        .findLockedByPaymentId(order.getPayment().getPaymentId()).orElse(null);
    if (payment == null || payment.getBalance() != order.getAmount()) {
      // 주문 뒤에 청구가 바뀌었거나 다른 경로로 이미 납부됐다.
      order.markFailed();
      return Outcome.BILL_CHANGED;
    }
    TuitionPaymentGateway.Approval approval =
        gateway.approve(order.getOrderId(), paymentKey, order.getAmount());
    if (!approval.approved()) {
      order.markFailed();
      return Outcome.DECLINED;
    }
    order.markPaid(paymentKey, now);
    payment.applyDeposit(order.getAmount(), now.toLocalDate(), PAY_METHOD);
    return Outcome.PAID;
  }
}
