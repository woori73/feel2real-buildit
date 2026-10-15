package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One durable optimization state machine per video. */
@Entity
@Table(
    name = "academy_video_optimization_jobs",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_academy_video_optimization_jobs_video",
        columnNames = "video_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyVideoOptimizationJob extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "job_id")
  private Long jobId;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "video_id", nullable = false, unique = true)
  private AcademyVideo video;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private VideoOptimizationStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at")
  private LocalDateTime nextAttemptAt;

  @Column(name = "lease_owner", length = 100)
  private String leaseOwner;

  @Column(name = "lease_token", length = 36)
  private String leaseToken;

  @Column(name = "lease_expires_at")
  private LocalDateTime leaseExpiresAt;

  @Column(name = "heartbeat_at")
  private LocalDateTime heartbeatAt;

  @Column(name = "progress_at")
  private LocalDateTime progressAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  @Column(name = "last_error_code", length = 80)
  private String lastErrorCode;

  @Column(name = "diagnostic_summary", length = 500)
  private String diagnosticSummary;

  @Builder
  private AcademyVideoOptimizationJob(
      AcademyVideo video,
      VideoOptimizationStatus status,
      int attemptCount,
      LocalDateTime nextAttemptAt,
      String leaseOwner,
      String leaseToken,
      LocalDateTime leaseExpiresAt,
      LocalDateTime heartbeatAt,
      LocalDateTime progressAt,
      LocalDateTime startedAt,
      LocalDateTime finishedAt,
      String lastErrorCode,
      String diagnosticSummary) {
    this.video = video;
    this.status = status;
    this.attemptCount = attemptCount;
    this.nextAttemptAt = nextAttemptAt;
    this.leaseOwner = leaseOwner;
    this.leaseToken = leaseToken;
    this.leaseExpiresAt = leaseExpiresAt;
    this.heartbeatAt = heartbeatAt;
    this.progressAt = progressAt;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.lastErrorCode = lastErrorCode;
    this.diagnosticSummary = diagnosticSummary;
    validateLeaseContract();
  }

  public static AcademyVideoOptimizationJob pending(AcademyVideo video, LocalDateTime now) {
    return AcademyVideoOptimizationJob.builder()
        .video(video)
        .status(VideoOptimizationStatus.PENDING)
        .attemptCount(0)
        .nextAttemptAt(now)
        .build();
  }

  public boolean claim(
      String owner, String token, LocalDateTime now, LocalDateTime leaseUntil) {
    if (status != VideoOptimizationStatus.PENDING
        && status != VideoOptimizationStatus.RETRY_WAIT
        || attemptCount >= 2
        || nextAttemptAt == null
        || nextAttemptAt.isAfter(now)) {
      return false;
    }
    status = VideoOptimizationStatus.PROCESSING;
    attemptCount++;
    nextAttemptAt = null;
    leaseOwner = owner;
    leaseToken = token;
    leaseExpiresAt = leaseUntil;
    heartbeatAt = now;
    progressAt = now;
    if (startedAt == null) startedAt = now;
    finishedAt = null;
    lastErrorCode = null;
    diagnosticSummary = null;
    return true;
  }

  public boolean leaseMatches(String token) {
    return status == VideoOptimizationStatus.PROCESSING
        && token != null
        && token.equals(leaseToken);
  }

  public boolean retry(String token, String code, String diagnostic, LocalDateTime now) {
    if (!leaseMatches(token) || attemptCount != 1) return false;
    status = VideoOptimizationStatus.RETRY_WAIT;
    nextAttemptAt = now.plusSeconds(60);
    clearLease();
    lastErrorCode = safe(code, 80);
    diagnosticSummary = safe(diagnostic, 500);
    return true;
  }

  public boolean fail(String token, String code, String diagnostic, LocalDateTime now) {
    if (!leaseMatches(token) || attemptCount != 2) return false;
    status = VideoOptimizationStatus.FAILED;
    nextAttemptAt = null;
    clearLease();
    finishedAt = now;
    lastErrorCode = safe(code, 80);
    diagnosticSummary = safe(diagnostic, 500);
    return true;
  }

  public boolean cancel(String token, String code, LocalDateTime now) {
    if (!leaseMatches(token)) return false;
    status = VideoOptimizationStatus.CANCELLED;
    nextAttemptAt = null;
    clearLease();
    finishedAt = now;
    lastErrorCode = safe(code, 80);
    diagnosticSummary = null;
    return true;
  }

  public boolean cancelBeforeClaim(String code, LocalDateTime now) {
    if (status != VideoOptimizationStatus.PENDING
        && status != VideoOptimizationStatus.RETRY_WAIT) {
      return false;
    }
    status = VideoOptimizationStatus.CANCELLED;
    nextAttemptAt = null;
    finishedAt = now;
    lastErrorCode = safe(code, 80);
    diagnosticSummary = null;
    return true;
  }

  /** Owner deletion cancels every state that could still publish or retry. */
  public String cancelForDeletion(LocalDateTime now) {
    if (status != VideoOptimizationStatus.PENDING
        && status != VideoOptimizationStatus.RETRY_WAIT
        && status != VideoOptimizationStatus.PROCESSING) {
      return null;
    }
    String invalidatedToken = leaseToken;
    status = VideoOptimizationStatus.CANCELLED;
    nextAttemptAt = null;
    clearLease();
    finishedAt = now;
    lastErrorCode = "VIDEO_DELETING";
    diagnosticSummary = null;
    return invalidatedToken;
  }

  public void recoverExpired(LocalDateTime now) {
    if (status != VideoOptimizationStatus.PROCESSING
        || leaseExpiresAt == null
        || leaseExpiresAt.isAfter(now)) {
      return;
    }
    if (attemptCount == 1) {
      status = VideoOptimizationStatus.RETRY_WAIT;
      nextAttemptAt = now.plusSeconds(60);
      lastErrorCode = "LEASE_EXPIRED";
    } else {
      status = VideoOptimizationStatus.FAILED;
      nextAttemptAt = null;
      finishedAt = now;
      lastErrorCode = "LEASE_EXPIRED";
    }
    clearLease();
  }

  public boolean succeed(String token, LocalDateTime now) {
    if (!leaseMatches(token)) return false;
    status = VideoOptimizationStatus.SUCCEEDED;
    nextAttemptAt = null;
    clearLease();
    finishedAt = now;
    lastErrorCode = null;
    diagnosticSummary = null;
    return true;
  }

  private void clearLease() {
    leaseOwner = null;
    leaseToken = null;
    leaseExpiresAt = null;
    heartbeatAt = null;
  }

  private static String safe(String value, int max) {
    if (value == null) return null;
    String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }

  private void validateLeaseContract() {
    if (video == null || status == null) {
      throw new IllegalArgumentException("video and status are required");
    }
    if (attemptCount < 0 || attemptCount > 2) {
      throw new IllegalArgumentException("attemptCount must be between 0 and 2");
    }
    boolean processing = status == VideoOptimizationStatus.PROCESSING;
    boolean completeLease = leaseOwner != null
        && leaseToken != null
        && leaseExpiresAt != null
        && heartbeatAt != null;
    boolean anyLease = leaseOwner != null
        || leaseToken != null
        || leaseExpiresAt != null
        || heartbeatAt != null;
    if (processing && (attemptCount < 1 || !completeLease)) {
      throw new IllegalArgumentException("PROCESSING requires attempt and complete lease");
    }
    if (!processing && anyLease) {
      throw new IllegalArgumentException("non-PROCESSING state must clear lease fields");
    }
  }
}
