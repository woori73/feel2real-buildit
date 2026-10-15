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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Durable cleanup outbox row. Object deletion precedes the conditional DONE transition. */
@Entity
@Table(name = "academy_video_asset_cleanups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyVideoAssetCleanup extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "cleanup_id")
  private Long cleanupId;

  @Column(name = "dedupe_key", nullable = false, unique = true, length = 64)
  private String dedupeKey;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id")
  private AcademyVideoUploadSession session;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "video_id")
  private AcademyVideo video;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id")
  private AcademyVideoOptimizationJob job;

  @Enumerated(EnumType.STRING)
  @Column(name = "asset_class", nullable = false, length = 40)
  private VideoAssetClass assetClass;

  @Column(name = "asset_key", nullable = false, length = 500)
  private String assetKey;

  @Column(name = "reason", nullable = false, length = 80)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 20)
  private VideoAssetCleanupState state;

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

  @Column(name = "last_error_code", length = 80)
  private String lastErrorCode;

  @Builder
  private AcademyVideoAssetCleanup(
      String dedupeKey,
      AcademyVideoUploadSession session,
      AcademyVideo video,
      AcademyVideoOptimizationJob job,
      VideoAssetClass assetClass,
      String assetKey,
      String reason,
      VideoAssetCleanupState state,
      int attemptCount,
      LocalDateTime nextAttemptAt,
      String leaseOwner,
      String leaseToken,
      LocalDateTime leaseExpiresAt,
      String lastErrorCode) {
    this.dedupeKey = requireText(dedupeKey, "dedupeKey");
    this.session = session;
    this.video = video;
    this.job = job;
    this.assetClass = assetClass;
    this.assetKey = requireText(assetKey, "assetKey");
    this.reason = requireText(reason, "reason");
    this.state = state != null ? state : VideoAssetCleanupState.PENDING;
    this.attemptCount = attemptCount;
    this.nextAttemptAt = nextAttemptAt;
    this.leaseOwner = leaseOwner;
    this.leaseToken = leaseToken;
    this.leaseExpiresAt = leaseExpiresAt;
    this.lastErrorCode = lastErrorCode;
    validate();
  }

  private void validate() {
    if (dedupeKey.length() != 64 || assetClass == null || attemptCount < 0) {
      throw new IllegalArgumentException("cleanup identity is invalid");
    }
    boolean processing = state == VideoAssetCleanupState.PROCESSING;
    boolean completeLease =
        leaseOwner != null && leaseToken != null && leaseExpiresAt != null;
    boolean anyLease =
        leaseOwner != null || leaseToken != null || leaseExpiresAt != null;
    if (processing != completeLease || (!processing && anyLease)) {
      throw new IllegalArgumentException("cleanup lease fields do not match state");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  public static AcademyVideoAssetCleanup pending(
      String dedupeKey,
      AcademyVideo video,
      AcademyVideoOptimizationJob job,
      VideoAssetClass assetClass,
      String assetKey,
      String reason,
      LocalDateTime now) {
    return AcademyVideoAssetCleanup.builder()
        .dedupeKey(dedupeKey)
        .video(video)
        .job(job)
        .assetClass(assetClass)
        .assetKey(assetKey)
        .reason(reason)
        .state(VideoAssetCleanupState.PENDING)
        .attemptCount(0)
        .nextAttemptAt(now)
        .build();
  }

  public boolean claim(
      String owner, String token, LocalDateTime now, LocalDateTime leaseUntil) {
    boolean duePending = state == VideoAssetCleanupState.PENDING
        && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    boolean expiredProcessing = state == VideoAssetCleanupState.PROCESSING
        && leaseExpiresAt != null && !leaseExpiresAt.isAfter(now);
    if (!duePending && !expiredProcessing) {
      return false;
    }
    state = VideoAssetCleanupState.PROCESSING;
    attemptCount++;
    nextAttemptAt = null;
    leaseOwner = requireText(owner, "owner");
    leaseToken = requireText(token, "token");
    leaseExpiresAt = leaseUntil;
    lastErrorCode = null;
    return true;
  }

  public boolean complete(String token) {
    if (!leaseMatches(token)) return false;
    state = VideoAssetCleanupState.DONE;
    nextAttemptAt = null;
    clearLease();
    lastErrorCode = null;
    return true;
  }

  public boolean retry(
      String token, String safeErrorCode, LocalDateTime nextAttemptAt) {
    if (!leaseMatches(token)) return false;
    state = VideoAssetCleanupState.PENDING;
    this.nextAttemptAt = nextAttemptAt;
    clearLease();
    lastErrorCode = safe(safeErrorCode, 80);
    return true;
  }

  private boolean leaseMatches(String token) {
    return state == VideoAssetCleanupState.PROCESSING
        && token != null && token.equals(leaseToken);
  }

  private void clearLease() {
    leaseOwner = null;
    leaseToken = null;
    leaseExpiresAt = null;
  }

  private static String safe(String value, int max) {
    if (value == null || value.isBlank()) return "CLEANUP_FAILED";
    String normalized = value.replaceAll("[^A-Za-z0-9_\\-]", "_").toUpperCase();
    return normalized.substring(0, Math.min(max, normalized.length()));
  }
}
