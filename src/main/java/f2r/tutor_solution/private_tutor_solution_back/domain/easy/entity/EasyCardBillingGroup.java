package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/** Provider observation grouping; billing never creates a new expense. */
@Entity
@Table(name = "easy_card_billing_groups",
    uniqueConstraints = @UniqueConstraint(name = "uq_easy_card_billing_fingerprint",
        columnNames = {"business_id", "fingerprint"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCardBillingGroup extends BaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "billing_group_id") private Long billingGroupId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false) private EasyBusiness business;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "instrument_id", nullable = false) private EasyCardInstrument instrument;
  @Column(name = "fingerprint", nullable = false, length = 64) private String fingerprint;
  @Column(name = "period_start") private LocalDate periodStart;
  @Column(name = "period_end") private LocalDate periodEnd;
  @Column(name = "payment_due_on") private LocalDate paymentDueOn;
  @Column(name = "observed_total", nullable = false) private long observedTotal;
  @Column(name = "currency", nullable = false, length = 3) private String currency;

  @Builder
  public EasyCardBillingGroup(EasyBusiness business, EasyCardInstrument instrument,
      String fingerprint, LocalDate periodStart, LocalDate periodEnd, LocalDate paymentDueOn,
      long observedTotal, String currency) {
    if (business == null || instrument == null || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid billing group.");
    if (observedTotal < 0) throw new IllegalArgumentException("Billing total cannot be negative.");
    this.business = business; this.instrument = instrument; this.fingerprint = fingerprint;
    this.periodStart = periodStart; this.periodEnd = periodEnd; this.paymentDueOn = paymentDueOn;
    this.observedTotal = observedTotal; this.currency = currency == null ? "KRW" : currency;
  }
}
