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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집 실행(run) provenance — 언제, 어떤 어댑터로 몇 건을 수집/중복제외/실패했고
 * 어떤 import batch를 만들었는지 기록한다. 회계 데이터가 아니다.
 */
@Entity
@Table(name = "easy_collection_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCollectionRun extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "run_id")
  private Long runId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connection_id", nullable = false)
  private EasyCollectionConnection connection;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "trigger_type", nullable = false, length = 10)
  private String triggerType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EasyCollectionRunStatus status;

  @Column(name = "source_filename", nullable = false, length = 200)
  private String sourceFilename;

  @Column(name = "adapter_code", nullable = false, length = 40)
  private String adapterCode;

  @Column(name = "adapter_version", nullable = false, length = 20)
  private String adapterVersion;

  @Column(name = "fetched_count", nullable = false)
  private int fetchedCount;

  @Column(name = "accepted_count", nullable = false)
  private int acceptedCount;

  @Column(name = "duplicate_count", nullable = false)
  private int duplicateCount;

  @Column(name = "error_count", nullable = false)
  private int errorCount;

  @Column(name = "import_batch_id")
  private Long importBatchId;

  @Column(name = "error_summary", length = 500)
  private String errorSummary;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "lease_token", length = 64)
  private String leaseToken;

  @Column(name = "lease_acquired_at")
  private LocalDateTime leaseAcquiredAt;

  @Column(name = "lease_expires_at")
  private LocalDateTime leaseExpiresAt;

  @Column(name = "heartbeat_at")
  private LocalDateTime heartbeatAt;

  /** 마지막 기관 API 응답 수신 시각 — 장부 저장 완료와 구분한다. */
  @Column(name = "fetched_at")
  private LocalDateTime fetchedAt;

  /** 중앙 장부 저장까지 끝난 마지막 조회일. 저장 실패 구간은 포함하지 않는다. */
  @Column(name = "ledger_saved_through")
  private java.time.LocalDate ledgerSavedThrough;

  @Column(name = "ledger_saved_at")
  private LocalDateTime ledgerSavedAt;

  @Column(name = "ledger_saved_count", nullable = false)
  private int ledgerSavedCount;

  @Column(name = "ledger_review_count", nullable = false)
  private int ledgerReviewCount;

  @Column(name = "ledger_classified_count", nullable = false)
  private int ledgerClassifiedCount;

  @Builder
  public EasyCollectionRun(EasyCollectionConnection connection, EasyBusiness business,
      String triggerType, String sourceFilename, String adapterCode, String adapterVersion) {
    this.connection = connection;
    this.business = business;
    this.triggerType = triggerType == null ? "MANUAL" : triggerType;
    this.status = EasyCollectionRunStatus.RUNNING;
    this.sourceFilename = sourceFilename == null ? "" : sourceFilename;
    this.adapterCode = adapterCode == null ? "" : adapterCode;
    this.adapterVersion = adapterVersion == null ? "" : adapterVersion;
    this.startedAt = LocalDateTime.now();
  }

  public void claimLease(String token, LocalDateTime acquiredAt, LocalDateTime expiresAt) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Collection lease token is required.");
    }
    this.leaseToken = token;
    this.leaseAcquiredAt = acquiredAt;
    this.heartbeatAt = acquiredAt;
    this.leaseExpiresAt = expiresAt;
  }

  public void heartbeat(String token, LocalDateTime heartbeatAt, LocalDateTime expiresAt) {
    if (!isOwnedBy(token) || status != EasyCollectionRunStatus.RUNNING) {
      throw new IllegalStateException("Collection run lease is not owned by this execution.");
    }
    this.heartbeatAt = heartbeatAt;
    this.leaseExpiresAt = expiresAt;
  }

  public boolean isOwnedBy(String token) {
    return token != null && token.equals(this.leaseToken);
  }

  public void finish(int fetched, int accepted, int duplicate, int error, Long importBatchId,
      String errorSummary) {
    this.fetchedCount = fetched;
    this.acceptedCount = accepted;
    this.duplicateCount = duplicate;
    this.errorCount = error;
    this.importBatchId = importBatchId;
    this.errorSummary = errorSummary == null || errorSummary.isBlank()
        ? null : truncate(errorSummary);
    this.status = error > 0
        ? (accepted + duplicate > 0 ? EasyCollectionRunStatus.PARTIAL : EasyCollectionRunStatus.FAILED)
        : EasyCollectionRunStatus.SUCCESS;
    this.completedAt = LocalDateTime.now();
  }

  public void recordFetched(LocalDateTime when) {
    this.fetchedAt = when;
  }

  /** 한 저장 단위(기간 구간)의 장부 저장이 커밋될 때마다 누적한다. */
  public void addLedgerResult(int saved, int review, int classified,
      java.time.LocalDate savedThrough, LocalDateTime when) {
    this.ledgerSavedCount += saved;
    this.ledgerReviewCount += review;
    this.ledgerClassifiedCount += classified;
    if (savedThrough != null) {
      this.ledgerSavedThrough = savedThrough;
    }
    this.ledgerSavedAt = when;
  }

  /** 구간별 저장 결과를 누적한 뒤 최종 상태를 정한다. */
  public void accumulate(int fetched, int accepted, int duplicate, int error, Long importBatchId) {
    this.fetchedCount += fetched;
    this.acceptedCount += accepted;
    this.duplicateCount += duplicate;
    this.errorCount += error;
    if (importBatchId != null) {
      this.importBatchId = importBatchId;
    }
  }

  /** 누적 카운터로 종료한다. 실패 구간이 있었으면 failureSummary를 남기고 PARTIAL/FAILED로 둔다. */
  public void complete(String failureSummary) {
    boolean failed = failureSummary != null && !failureSummary.isBlank();
    this.errorSummary = failed ? truncate(failureSummary)
        : (errorSummary == null || errorSummary.isBlank() ? null : errorSummary);
    boolean anyProgress = acceptedCount + duplicateCount > 0;
    if (failed) {
      this.status = anyProgress ? EasyCollectionRunStatus.PARTIAL : EasyCollectionRunStatus.FAILED;
      if (errorCount == 0) {
        this.errorCount = 1;
      }
    } else {
      this.status = errorCount > 0
          ? (anyProgress ? EasyCollectionRunStatus.PARTIAL : EasyCollectionRunStatus.FAILED)
          : EasyCollectionRunStatus.SUCCESS;
    }
    this.completedAt = LocalDateTime.now();
  }

  public void appendRowErrors(String summary) {
    if (summary == null || summary.isBlank()) {
      return;
    }
    this.errorSummary = truncate(errorSummary == null ? summary : errorSummary + " / " + summary);
  }

  public void fail(String reason) {
    this.status = EasyCollectionRunStatus.FAILED;
    this.errorCount = 1;
    this.errorSummary = truncate(reason);
    this.completedAt = LocalDateTime.now();
  }

  private static String truncate(String value) {
    return value.length() <= 500 ? value : value.substring(0, 497) + "...";
  }
}
