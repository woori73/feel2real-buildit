package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제창 주문(시험 모드). 금액은 서버가 정한 값이고, 카드번호 같은 결제 수단 정보는 저장하지 않는다.
 */
@Entity
@Table(name = "easy_tuition_pg_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyTuitionPgOrder extends BaseEntity {

  public enum Status { READY, PAID, FAILED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "order_pk")
  private Long orderPk;

  @Column(name = "order_id", nullable = false, length = 40, unique = true)
  private String orderId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "link_id", nullable = false)
  private EasyTuitionBillLink link;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id", nullable = false)
  private EasyTuitionPayment payment;

  @Column(name = "amount", nullable = false)
  private long amount;

  @Column(name = "gateway", nullable = false, length = 20)
  private String gateway;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status;

  @Column(name = "payment_key", length = 200, unique = true)
  private String paymentKey;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Builder
  public EasyTuitionPgOrder(String orderId, EasyTuitionBillLink link, EasyTuitionPayment payment,
      long amount, String gateway) {
    this.orderId = orderId;
    this.link = link;
    this.payment = payment;
    this.amount = amount;
    this.gateway = gateway;
    this.status = Status.READY;
  }

  public void markPaid(String paymentKey, LocalDateTime now) {
    this.status = Status.PAID;
    this.paymentKey = paymentKey;
    this.paidAt = now;
  }

  public void markFailed() {
    this.status = Status.FAILED;
  }
}
