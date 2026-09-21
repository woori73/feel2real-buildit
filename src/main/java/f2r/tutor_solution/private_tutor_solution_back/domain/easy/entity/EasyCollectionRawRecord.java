package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부에서 수집한 원본 레코드의 불변 보존. 회계 transaction이 아니라 수집 provenance다.
 * 한 번 저장한 raw_payload/dedupe_key는 수정하지 않는다(상태 전이만 허용).
 * dedupe_key UNIQUE가 파일 재수집 멱등성의 1차 방어.
 */
@Entity
@Table(name = "easy_collection_raw_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCollectionRawRecord extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "raw_id")
  private Long rawId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "run_id", nullable = false)
  private EasyCollectionRun run;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connection_id", nullable = false)
  private EasyCollectionConnection connection;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "source_code", nullable = false, length = 40)
  private String sourceCode;

  @Column(name = "external_record_id", length = 100)
  private String externalRecordId;

  @Column(name = "tuple_hash", nullable = false, length = 64)
  private String tupleHash;

  @Column(name = "dedupe_key", nullable = false, length = 160, unique = true)
  private String dedupeKey;

  @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
  private String rawPayload;

  @Column(name = "raw_hash", nullable = false, length = 64)
  private String rawHash;

  @Column(name = "source_occurred_on")
  private LocalDate sourceOccurredOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private EasyCollectionRawStatus status;

  @Column(name = "normalize_error", length = 500)
  private String normalizeError;

  @Column(name = "import_batch_id")
  private Long importBatchId;

  @Column(name = "import_row_id")
  private Long importRowId;

  @Column(name = "ingested_at", nullable = false)
  private LocalDateTime ingestedAt;

  @Builder
  public EasyCollectionRawRecord(EasyCollectionRun run, EasyCollectionConnection connection,
      EasyBusiness business, String sourceCode, String externalRecordId, String tupleHash,
      String dedupeKey, String rawPayload, String rawHash, LocalDate sourceOccurredOn,
      EasyCollectionRawStatus status, String normalizeError) {
    this.run = run;
    this.connection = connection;
    this.business = business;
    this.sourceCode = sourceCode;
    this.externalRecordId = externalRecordId;
    this.tupleHash = tupleHash;
    this.dedupeKey = dedupeKey;
    this.rawPayload = rawPayload;
    this.rawHash = rawHash;
    this.sourceOccurredOn = sourceOccurredOn;
    this.status = status;
    this.normalizeError = normalizeError;
    this.ingestedAt = LocalDateTime.now();
  }

  public void markLedgerDuplicate() {
    this.status = EasyCollectionRawStatus.LEDGER_DUPLICATE_EXCLUDED;
  }

  public void linkImport(Long batchId, Long rowId) {
    this.importBatchId = batchId;
    this.importRowId = rowId;
    this.status = EasyCollectionRawStatus.IMPORT_LINKED;
  }
}
