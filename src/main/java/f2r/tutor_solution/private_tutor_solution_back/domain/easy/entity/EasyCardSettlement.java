package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/** Bank-payment settlement candidate. This entity never represents a new expense. */
@Entity
@Table(name = "easy_card_settlements",
    uniqueConstraints = @UniqueConstraint(name = "uq_easy_card_settlement_fingerprint",
        columnNames = {"business_id", "fingerprint"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCardSettlement extends BaseEntity {
  public enum Status { UNALLOCATED, PARTIALLY_ALLOCATED, ALLOCATED }
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "settlement_id") private Long settlementId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false) private EasyBusiness business;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "instrument_id", nullable = false) private EasyCardInstrument instrument;
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "settlement_event_id", nullable = false) private EasyCardEvent settlementEvent;
  @Column(name = "bank_transaction_ref_hash", length = 64) private String bankTransactionRefHash;
  @Column(name = "fingerprint", nullable = false, length = 64) private String fingerprint;
  @Column(name = "paid_on", nullable = false) private LocalDate paidOn;
  @Column(name = "amount", nullable = false) private long amount;
  @Column(name = "allocated_amount", nullable = false) private long allocatedAmount;
  @Column(name = "currency", nullable = false, length = 3) private String currency;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24) private Status status;

  @Builder
  public EasyCardSettlement(EasyBusiness business, EasyCardInstrument instrument,
      EasyCardEvent settlementEvent, String bankTransactionRefHash, String fingerprint,
      LocalDate paidOn, long amount, String currency) {
    if (business == null || instrument == null || settlementEvent == null || paidOn == null)
      throw new IllegalArgumentException("Invalid settlement ownership.");
    if (settlementEvent.getEventType() != EasyCardEvent.Type.PAYMENT_SETTLEMENT || amount <= 0)
      throw new IllegalArgumentException("Invalid settlement event or amount.");
    this.business = business; this.instrument = instrument; this.settlementEvent = settlementEvent;
    this.bankTransactionRefHash = bankTransactionRefHash; this.fingerprint = fingerprint;
    this.paidOn = paidOn; this.amount = amount; this.currency = currency == null ? "KRW" : currency;
    this.status = Status.UNALLOCATED;
  }
  public long remainingAmount() { return amount - allocatedAmount; }
  public void allocate(long value) {
    if (value <= 0 || value > remainingAmount())
      throw new IllegalArgumentException("Allocation exceeds settlement remainder.");
    allocatedAmount += value;
    status = allocatedAmount == amount ? Status.ALLOCATED : Status.PARTIALLY_ALLOCATED;
  }
}
