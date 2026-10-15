package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Immutable provider observation attached to an economic sale. An event is provenance, not an
 * accounting transaction or an instruction to create income.
 */
@Entity
@Table(name = "easy_collection_events",
    uniqueConstraints = @UniqueConstraint(name = "uq_collection_event_key",
        columnNames = {"connection_id", "event_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCollectionEvent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  private Long eventId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private EasyCollectionConnection connection;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_id", nullable = false)
  private EasyEconomicSale economicSale;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "raw_id")
  private EasyCollectionRawRecord rawRecord;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "original_event_id")
  private EasyCollectionEvent originalEvent;

  @Enumerated(EnumType.STRING)
  @Column(name = "dataset_type", nullable = false, length = 20)
  private EasyCollectionDatasetType datasetType;

  @Column(name = "event_key", nullable = false, length = 200)
  private String eventKey;

  @Column(name = "external_event_id", length = 160)
  private String externalEventId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EasyCollectionEventStatus status;

  @Column(name = "occurred_at")
  private LocalDateTime occurredAt;

  @Column(name = "provider_cursor", length = 500)
  private String providerCursor;

  @Column(name = "provider_version", length = 100)
  private String providerVersion;

  @Column(name = "received_at", nullable = false)
  private LocalDateTime receivedAt;

  @Builder
  public EasyCollectionEvent(EasyCollectionConnection connection, EasyBusiness business,
      EasyEconomicSale economicSale, EasyCollectionRawRecord rawRecord,
      EasyCollectionEvent originalEvent, EasyCollectionDatasetType datasetType, String eventKey,
      String externalEventId, LocalDateTime occurredAt, String providerCursor,
      String providerVersion) {
    this.connection = connection;
    this.business = business;
    this.economicSale = economicSale;
    this.rawRecord = rawRecord;
    this.originalEvent = originalEvent;
    this.datasetType = datasetType;
    this.eventKey = eventKey;
    this.externalEventId = externalEventId;
    this.status = EasyCollectionEventStatus.OBSERVED;
    this.occurredAt = occurredAt;
    this.providerCursor = providerCursor;
    this.providerVersion = providerVersion;
    this.receivedAt = LocalDateTime.now();
  }

  public void markReversed() {
    this.status = EasyCollectionEventStatus.REVERSED;
  }

  public void markSuperseded() {
    this.status = EasyCollectionEventStatus.SUPERSEDED;
  }
}
