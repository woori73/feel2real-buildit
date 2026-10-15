package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

/**
 * One "가져오기" request against one local collector: STARTING -&gt; BROWSER_OPEN -&gt;
 * WAITING_FOR_USER_AUTH -&gt; AUTHENTICATED -&gt; COLLECTING -&gt; DOWNLOADING -&gt; COMPLETED, in
 * strict order. FAILED/CANCELLED are reachable from any non-terminal state. EASY never receives
 * the certificate/password/OTP the human enters during WAITING_FOR_USER_AUTH — only status
 * transitions and, on completion, file metadata (name/size/hash), never file content.
 */
@Entity
@Table(name = "easy_collector_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCollectorTask extends BaseEntity {

  /** Forward order of the happy path; fail()/cancel() may fire from any non-terminal state. */
  private static final List<EasyCollectorTaskStatus> HAPPY_PATH = List.of(
      EasyCollectorTaskStatus.STARTING,
      EasyCollectorTaskStatus.BROWSER_OPEN,
      EasyCollectorTaskStatus.WAITING_FOR_USER_AUTH,
      EasyCollectorTaskStatus.AUTHENTICATED,
      EasyCollectorTaskStatus.COLLECTING,
      EasyCollectorTaskStatus.DOWNLOADING,
      EasyCollectorTaskStatus.COMPLETED);

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "task_id")
  private Long taskId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "collector_id", nullable = false)
  private EasyLocalCollector collector;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;
  @Column(name = "source_code", nullable = false, length = 40)
  private String sourceCode;
  @Column(name = "data_type", nullable = false, length = 100)
  private String dataType;
  @Column(name = "requested_from", nullable = false)
  private LocalDate requestedFrom;
  @Column(name = "requested_to", nullable = false)
  private LocalDate requestedTo;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private EasyCollectorTaskStatus status = EasyCollectorTaskStatus.STARTING;
  @Column(name = "error_summary", length = 500)
  private String errorSummary;
  @Column(name = "downloaded_filename", length = 200)
  private String downloadedFilename;
  @Column(name = "downloaded_size")
  private Long downloadedSize;
  @Column(name = "downloaded_sha256", length = 64)
  private String downloadedSha256;
  @Column(name = "downloaded_at")
  private LocalDateTime downloadedAt;
  @Column(name = "started_at")
  private LocalDateTime startedAt;
  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Builder
  public EasyCollectorTask(EasyLocalCollector collector, EasyBusiness business,
      String sourceCode, String dataType, LocalDate requestedFrom, LocalDate requestedTo) {
    if (collector == null || business == null)
      throw new IllegalArgumentException("collector and business are required.");
    if (sourceCode == null || sourceCode.isBlank())
      throw new IllegalArgumentException("sourceCode is required.");
    if (dataType == null || dataType.isBlank())
      throw new IllegalArgumentException("dataType is required.");
    if (requestedFrom == null || requestedTo == null || requestedFrom.isAfter(requestedTo))
      throw new IllegalArgumentException("requestedFrom must be on or before requestedTo.");
    this.collector = collector;
    this.business = business;
    this.sourceCode = sourceCode.trim();
    this.dataType = dataType.trim();
    this.requestedFrom = requestedFrom;
    this.requestedTo = requestedTo;
  }

  /** Advance exactly one step along the happy path. Rejects skips and backward moves. */
  public void advanceTo(EasyCollectorTaskStatus next) {
    if (status.isTerminal())
      throw new IllegalStateException("Task is already terminal: " + status);
    int currentIdx = HAPPY_PATH.indexOf(status);
    int nextIdx = HAPPY_PATH.indexOf(next);
    if (currentIdx < 0 || nextIdx != currentIdx + 1)
      throw new IllegalStateException("Cannot advance from " + status + " to " + next);
    this.status = next;
    if (next == EasyCollectorTaskStatus.BROWSER_OPEN) this.startedAt = LocalDateTime.now();
  }

  public void complete(String filename, long size, String sha256) {
    if (filename == null || filename.isBlank())
      throw new IllegalArgumentException("filename is required.");
    if (size <= 0) throw new IllegalArgumentException("size must be positive.");
    if (sha256 == null || !sha256.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("sha256 must be a SHA-256 hex digest.");
    advanceTo(EasyCollectorTaskStatus.COMPLETED);
    this.downloadedFilename = filename.trim();
    this.downloadedSize = size;
    this.downloadedSha256 = sha256;
    this.downloadedAt = LocalDateTime.now();
    this.completedAt = LocalDateTime.now();
  }

  public void fail(String reason) {
    if (status.isTerminal())
      throw new IllegalStateException("Task is already terminal: " + status);
    this.status = EasyCollectorTaskStatus.FAILED;
    this.errorSummary = reason == null || reason.isBlank() ? "unspecified" : reason.trim();
    this.completedAt = LocalDateTime.now();
  }

  public void cancel() {
    if (status.isTerminal())
      throw new IllegalStateException("Task is already terminal: " + status);
    this.status = EasyCollectorTaskStatus.CANCELLED;
    this.completedAt = LocalDateTime.now();
  }
}
