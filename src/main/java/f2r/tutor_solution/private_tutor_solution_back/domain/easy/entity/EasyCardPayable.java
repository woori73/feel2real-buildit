package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** Liability candidate created by one purchase observation. */
@Entity
@Table(name = "easy_card_payables",
    uniqueConstraints = @UniqueConstraint(name = "uq_easy_card_payable_purchase", columnNames = "purchase_event_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCardPayable extends BaseEntity {
  public enum Status { OPEN, PARTIALLY_SETTLED, SETTLED, CANCELLED }
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "payable_id") private Long payableId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false) private EasyBusiness business;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "instrument_id", nullable = false) private EasyCardInstrument instrument;
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_event_id", nullable = false) private EasyCardEvent purchaseEvent;
  @Column(name = "original_amount", nullable = false) private long originalAmount;
  @Column(name = "cancelled_amount", nullable = false) private long cancelledAmount;
  @Column(name = "settled_amount", nullable = false) private long settledAmount;
  @Column(name = "outstanding_amount", nullable = false) private long outstandingAmount;
  @Column(name = "currency", nullable = false, length = 3) private String currency;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24) private Status status;

  @Builder
  public EasyCardPayable(EasyBusiness business, EasyCardInstrument instrument,
      EasyCardEvent purchaseEvent, long originalAmount, String currency) {
    if (business == null || instrument == null || purchaseEvent == null)
      throw new IllegalArgumentException("Payable ownership is required.");
    if (purchaseEvent.getEventType() != EasyCardEvent.Type.PURCHASE)
      throw new IllegalArgumentException("Only PURCHASE creates a payable.");
    if (originalAmount <= 0) throw new IllegalArgumentException("Payable amount must be positive.");
    this.business = business;
    this.instrument = instrument;
    this.purchaseEvent = purchaseEvent;
    this.originalAmount = originalAmount;
    this.outstandingAmount = originalAmount;
    this.currency = currency == null || currency.isBlank() ? "KRW" : currency;
    this.status = Status.OPEN;
  }

  public long recognizedExpenseCandidate() { return originalAmount - cancelledAmount; }
  public void cancel(long amount) {
    requireAvailable(amount, "cancellation");
    cancelledAmount += amount;
    outstandingAmount -= amount;
    refreshStatus();
  }
  public void settle(long amount) {
    requireAvailable(amount, "settlement");
    settledAmount += amount;
    outstandingAmount -= amount;
    refreshStatus();
  }
  private void requireAvailable(long amount, String operation) {
    if (amount <= 0 || amount > outstandingAmount)
      throw new IllegalArgumentException(operation + " exceeds outstanding payable.");
  }
  private void refreshStatus() {
    if (outstandingAmount == 0) {
      status = cancelledAmount == originalAmount ? Status.CANCELLED : Status.SETTLED;
    } else if (settledAmount > 0) {
      status = Status.PARTIALLY_SETTLED;
    } else {
      status = Status.OPEN;
    }
  }
}
