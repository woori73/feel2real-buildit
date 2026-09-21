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
