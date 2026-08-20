package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Server-owned multipart reservation and durable finalization saga state.
 *
 * <p>The storage key/upload ID are created by the server and never accepted back as browser state.
 * Ownership is always derived through {@link #academy}; no independent owner column may diverge.
 */
@Entity
@Table(name = "academy_video_upload_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyVideoUploadSession extends BaseEntity {

  @Id
  @Column(name = "session_id", length = 36)
  private String sessionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @Column(name = "requested_filename", nullable = false, length = 255)
  private String requestedFilename;

  @Column(name = "declared_size_bytes", nullable = false)
  private long declaredSizeBytes;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 20)
  private VideoUploadSessionState state;

  @Column(name = "staging_key", nullable = false, length = 500)
  private String stagingKey;

  @Column(name = "multipart_upload_id", nullable = false, length = 500)
  private String multipartUploadId;

  @Column(name = "part_size_bytes", nullable = false)
  private long partSizeBytes;

  @Column(name = "expected_part_count", nullable = false)
  private int expectedPartCount;

  @Column(name = "deadline_at", nullable = false)
  private LocalDateTime deadlineAt;

  @Column(name = "finalization_token", length = 36)
  private String finalizationToken;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "part_manifest_json", columnDefinition = "json")
  private String partManifestJson;

  @Column(name = "manifest_hash", length = 64)
  private String manifestHash;

  @Column(name = "next_finalize_attempt_at")
  private LocalDateTime nextFinalizeAttemptAt;

  @Column(name = "finalize_recovery_owner", length = 100)
  private String finalizeRecoveryOwner;

  @Column(name = "finalize_recovery_lease_expires_at")
  private LocalDateTime finalizeRecoveryLeaseExpiresAt;

  @Column(name = "finalization_started_at")
  private LocalDateTime finalizationStartedAt;

  @Column(name = "finalization_completed_at")
  private LocalDateTime finalizationCompletedAt;

  @Column(name = "completed_object_etag", length = 255)
  private String completedObjectEtag;

  @Column(name = "completed_object_size_bytes")
  private Long completedObjectSizeBytes;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "accepted_video_id")
  private AcademyVideo acceptedVideo;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "aborted_at")
  private LocalDateTime abortedAt;

  @Column(name = "last_error_code", length = 80)
  private String lastErrorCode;

  @Builder
  private AcademyVideoUploadSession(
      String sessionId,
      Academy academy,
      String requestedFilename,
      long declaredSizeBytes,
      VideoUploadSessionState state,
      String stagingKey,
      String multipartUploadId,
      long partSizeBytes,
      int expectedPartCount,
      LocalDateTime deadlineAt,
      String finalizationToken,
      String partManifestJson,
      String manifestHash,
      LocalDateTime nextFinalizeAttemptAt,
      String finalizeRecoveryOwner,
      LocalDateTime finalizeRecoveryLeaseExpiresAt,
      LocalDateTime finalizationStartedAt,
      LocalDateTime finalizationCompletedAt,
      String completedObjectEtag,
      Long completedObjectSizeBytes,
      AcademyVideo acceptedVideo,
      LocalDateTime completedAt,
      LocalDateTime abortedAt,
      String lastErrorCode) {
    this.sessionId = requireText(sessionId, "sessionId");
    this.academy = academy;
    this.requestedFilename = requireText(requestedFilename, "requestedFilename");
    this.declaredSizeBytes = declaredSizeBytes;
    this.state = state;
    this.stagingKey = requireText(stagingKey, "stagingKey");
    this.multipartUploadId = requireText(multipartUploadId, "multipartUploadId");
    this.partSizeBytes = partSizeBytes;
    this.expectedPartCount = expectedPartCount;
    this.deadlineAt = deadlineAt;
    this.finalizationToken = finalizationToken;
    this.partManifestJson = partManifestJson;
    this.manifestHash = manifestHash;
    this.nextFinalizeAttemptAt = nextFinalizeAttemptAt;
    this.finalizeRecoveryOwner = finalizeRecoveryOwner;
    this.finalizeRecoveryLeaseExpiresAt = finalizeRecoveryLeaseExpiresAt;
    this.finalizationStartedAt = finalizationStartedAt;
    this.finalizationCompletedAt = finalizationCompletedAt;
    this.completedObjectEtag = completedObjectEtag;
    this.completedObjectSizeBytes = completedObjectSizeBytes;
    this.acceptedVideo = acceptedVideo;
    this.completedAt = completedAt;
    this.abortedAt = abortedAt;
    this.lastErrorCode = lastErrorCode;
    validate();
  }

  public boolean belongsToAcademy(Long academyId) {
    return academyId != null && academy != null && academyId.equals(academy.getAcademyId());
  }

  public void beginFinalization(
      String token, String manifestJson, String manifestHash, LocalDateTime now) {
    if (state != VideoUploadSessionState.INITIATED) {
      throw new IllegalStateException("only INITIATED sessions can begin finalization");
    }
    state = VideoUploadSessionState.COMPLETING;
    finalizationToken = requireText(token, "token");
    partManifestJson = requireText(manifestJson, "manifestJson");
    this.manifestHash = requireText(manifestHash, "manifestHash");
    finalizationStartedAt = now;
    // Gives the initiating request time to register its in-process cancellation handle.
    // A crash/restart has no handle, so recovery becomes eligible after this short grace.
    nextFinalizeAttemptAt = now.plusSeconds(2);
    lastErrorCode = null;
  }

  public void recordCompletedObject(String eTag, long sizeBytes) {
    if (state != VideoUploadSessionState.COMPLETING) {
      throw new IllegalStateException("session is not completing");
    }
    completedObjectEtag = eTag;
    completedObjectSizeBytes = sizeBytes;
  }

  public void scheduleRecovery(String safeErrorCode, LocalDateTime nextAttemptAt) {
    if (state != VideoUploadSessionState.COMPLETING) {
      return;
    }
    lastErrorCode = safeErrorCode;
    this.nextFinalizeAttemptAt = nextAttemptAt;
    finalizeRecoveryOwner = null;
    finalizeRecoveryLeaseExpiresAt = null;
  }

  public boolean claimRecovery(String owner, LocalDateTime now, LocalDateTime leaseExpiresAt) {
    if (state != VideoUploadSessionState.COMPLETING
        || nextFinalizeAttemptAt == null
        || nextFinalizeAttemptAt.isAfter(now)
        || finalizeRecoveryLeaseExpiresAt != null
            && !finalizeRecoveryLeaseExpiresAt.isBefore(now)) {
      return false;
    }
    finalizeRecoveryOwner = requireText(owner, "owner");
    finalizeRecoveryLeaseExpiresAt = leaseExpiresAt;
    return true;
  }

  public boolean renewRecoveryLease(
      String owner, LocalDateTime now, LocalDateTime leaseExpiresAt) {
    if (state != VideoUploadSessionState.COMPLETING
        || owner == null
        || !owner.equals(finalizeRecoveryOwner)
        || finalizeRecoveryLeaseExpiresAt == null
        || finalizeRecoveryLeaseExpiresAt.isBefore(now)) {
      return false;
    }
    finalizeRecoveryLeaseExpiresAt = leaseExpiresAt;
    return true;
  }

  public boolean isRecoveryOwnedBy(String owner, LocalDateTime now) {
    return owner != null
        && state == VideoUploadSessionState.COMPLETING
        && owner.equals(finalizeRecoveryOwner)
        && finalizeRecoveryLeaseExpiresAt != null
        && !finalizeRecoveryLeaseExpiresAt.isBefore(now);
  }

  public void clearRecoveryLease() {
    finalizeRecoveryOwner = null;
    finalizeRecoveryLeaseExpiresAt = null;
  }

  public void accept(AcademyVideo video, LocalDateTime now) {
    if (state != VideoUploadSessionState.COMPLETING) {
      throw new IllegalStateException("session is not completing");
    }
    state = VideoUploadSessionState.ACCEPTED;
    acceptedVideo = video;
    finalizationToken = null;
    finalizeRecoveryOwner = null;
    finalizeRecoveryLeaseExpiresAt = null;
    nextFinalizeAttemptAt = null;
    finalizationCompletedAt = now;
    completedAt = now;
    lastErrorCode = null;
  }

  public boolean abort(LocalDateTime now) {
    if (state != VideoUploadSessionState.INITIATED
        && state != VideoUploadSessionState.COMPLETING) {
      return false;
    }
    state = VideoUploadSessionState.ABORTED;
    finalizationToken = null;
    finalizeRecoveryOwner = null;
    finalizeRecoveryLeaseExpiresAt = null;
    nextFinalizeAttemptAt = null;
    abortedAt = now;
    lastErrorCode = "CLIENT_ABORT";
    return true;
  }

  public boolean expire(LocalDateTime now) {
    if (state != VideoUploadSessionState.INITIATED
        && state != VideoUploadSessionState.COMPLETING) {
      return false;
    }
    state = VideoUploadSessionState.EXPIRED;
    finalizationToken = null;
    finalizeRecoveryOwner = null;
    finalizeRecoveryLeaseExpiresAt = null;
    nextFinalizeAttemptAt = null;
    completedAt = now;
    lastErrorCode = "DEADLINE_EXCEEDED";
    return true;
  }

  public boolean reject(String safeErrorCode, LocalDateTime now) {
    if (state != VideoUploadSessionState.COMPLETING) {
      return false;
    }
    state = VideoUploadSessionState.REJECTED;
    finalizationToken = null;
    finalizeRecoveryOwner = null;
    finalizeRecoveryLeaseExpiresAt = null;
    nextFinalizeAttemptAt = null;
    completedAt = now;
    lastErrorCode = safeErrorCode;
    return true;
  }

  private void validate() {
    if (academy == null || deadlineAt == null || state == null) {
      throw new IllegalArgumentException("academy, deadlineAt and state are required");
    }
    if (declaredSizeBytes < 1 || declaredSizeBytes > 209_715_200L) {
      throw new IllegalArgumentException("declaredSizeBytes is out of bounds");
    }
    if (partSizeBytes <= 0 || expectedPartCount < 1 || expectedPartCount > 10) {
      throw new IllegalArgumentException("multipart shape is out of bounds");
    }
    boolean completing = state == VideoUploadSessionState.COMPLETING;
    boolean completeFinalizationIdentity =
        finalizationToken != null && partManifestJson != null && manifestHash != null;
    if ((completing && !completeFinalizationIdentity)
        || (!completing && finalizationToken != null)) {
      throw new IllegalArgumentException("COMPLETING requires immutable finalization identity");
    }
    boolean recoveryLeasePresent =
        finalizeRecoveryOwner != null || finalizeRecoveryLeaseExpiresAt != null;
    if (recoveryLeasePresent && (!completing
        || finalizeRecoveryOwner == null
        || finalizeRecoveryLeaseExpiresAt == null)) {
      throw new IllegalArgumentException("recovery lease fields must be complete and COMPLETING");
    }
    if ((state == VideoUploadSessionState.ACCEPTED) != (acceptedVideo != null)) {
      throw new IllegalArgumentException("accepted video must match ACCEPTED state");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
