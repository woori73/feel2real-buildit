package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/** Provider-neutral card observation. It is never an accounting transaction. */
@Entity
@Table(name = "easy_card_events",
    uniqueConstraints = @UniqueConstraint(name = "uq_easy_card_event_fingerprint",
        columnNames = {"business_id", "fingerprint"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCardEvent extends BaseEntity {
  public enum Type { APPROVAL, PURCHASE, CANCELLATION, CORRECTION, BILLING, PAYMENT_SETTLEMENT }
  public enum ReviewStatus { PENDING_REVIEW, APPROVED, REJECTED }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "card_event_id")
  private Long cardEventId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connection_id")
  private EasyCollectionConnection connection;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "instrument_id", nullable = false)
  private EasyCardInstrument instrument;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "original_event_id")
  private EasyCardEvent originalEvent;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "billing_group_id")
  private EasyCardBillingGroup billingGroup;
  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 24)
  private Type eventType;
  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false, length = 20)
  private ReviewStatus reviewStatus = ReviewStatus.PENDING_REVIEW;
  @Column(name = "provider_external_id", length = 160)
  private String providerExternalId;
  @Column(name = "fingerprint", nullable = false, length = 64)
  private String fingerprint;
  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;
  @Column(name = "amount", nullable = false)
  private long amount;
  @Column(name = "currency", nullable = false, length = 3)
  private String currency;
  @Column(name = "approval_reference_hash", length = 64)
  private String approvalReferenceHash;
  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;
  @Column(name = "reviewed_by", length = 50)
  private String reviewedBy;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "posted_txn_id")
  private EasyTransaction postedTxn;

  @Builder
  public EasyCardEvent(EasyBusiness business, EasyCollectionConnection connection,
      EasyCardInstrument instrument, EasyCardEvent originalEvent, Type eventType,
      String providerExternalId, String fingerprint, LocalDateTime occurredAt, long amount,
      String currency, String approvalReferenceHash) {
    if (business == null || instrument == null || eventType == null || occurredAt == null)
      throw new IllegalArgumentException("business, instrument, type and occurredAt are required.");
    if (amount <= 0) throw new IllegalArgumentException("Card event amount must be positive.");
    if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Event fingerprint must be SHA-256.");
    if ((eventType == Type.CANCELLATION || eventType == Type.CORRECTION) && originalEvent == null)
      throw new IllegalArgumentException("Cancellation/correction requires an original event.");
    this.business = business;
    this.connection = connection;
    this.instrument = instrument;
    this.originalEvent = originalEvent;
    this.eventType = eventType;
    this.providerExternalId = blankToNull(providerExternalId);
    this.fingerprint = fingerprint;
    this.occurredAt = occurredAt;
    this.amount = amount;
    this.currency = currency == null || currency.isBlank() ? "KRW" : currency.trim().toUpperCase();
    this.approvalReferenceHash = blankToNull(approvalReferenceHash);
  }

  public void assignBillingGroup(EasyCardBillingGroup group) { this.billingGroup = group; }
  public void approve(String reviewer) {
    if (reviewer == null || reviewer.isBlank()) throw new IllegalArgumentException("reviewer is required.");
    this.reviewStatus = ReviewStatus.APPROVED;
    this.reviewedBy = reviewer.trim();
    this.reviewedAt = LocalDateTime.now();
  }
  /** Approve and link the ledger transaction this event was posted as, atomically. */
  public void markPosted(EasyTransaction transaction, String reviewer) {
    if (transaction == null) throw new IllegalArgumentException("transaction is required.");
    approve(reviewer);
    this.postedTxn = transaction;
  }
  public void reject(String reviewer) {
    if (reviewer == null || reviewer.isBlank()) throw new IllegalArgumentException("reviewer is required.");
    if (this.postedTxn != null) throw new IllegalArgumentException("Posted events cannot be rejected.");
    this.reviewStatus = ReviewStatus.REJECTED;
    this.reviewedBy = reviewer.trim();
    this.reviewedAt = LocalDateTime.now();
  }
  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
