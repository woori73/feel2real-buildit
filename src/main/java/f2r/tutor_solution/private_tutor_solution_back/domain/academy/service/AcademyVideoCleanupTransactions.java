package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoAssetCleanup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoOptimizationJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoUploadSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoActiveSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetCleanupState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoRawCleanupStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoAssetCleanupRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoUploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Short database boundaries for cleanup claiming, fencing, and FK-safe final deletion. */
@Service
@RequiredArgsConstructor
public class AcademyVideoCleanupTransactions {
  private static final String DELETE_REASON = "VIDEO_DELETING";

  private final AcademyVideoAssetCleanupRepository cleanupRepository;
  private final AcademyVideoRepository videoRepository;
  private final AcademyVideoOptimizationJobRepository jobRepository;
  private final AcademyVideoUploadSessionRepository sessionRepository;
  private final AcademyVideoOptimizationCancellation cancellation;
  private final AcademyVideoUploadProperties properties;

  /**
   * Caller already owns the video row lock and has completed the lesson-media reference guard.
   * The signal is intentionally sent before commit: cancelling harmless work on rollback is safer
   * than allowing a process to publish while delete waits for commit.
   */
  @Transactional
  public void tombstoneLocked(AcademyVideo video) {
    LocalDateTime now = cleanupRepository.databaseNow();
    video.markDeleting();
    AcademyVideoOptimizationJob job =
        jobRepository.findForUpdateByVideoId(video.getVideoId()).orElse(null);
    String invalidatedToken = job == null ? null : job.cancelForDeletion(now);
    if (job != null && invalidatedToken != null) {
      cancellation.signal(job.getJobId(), invalidatedToken);
    }

    enqueue(video, null, job, VideoAssetClass.RAW_VIDEO,
        video.getRawStorageKey(), DELETE_REASON, now);
    enqueue(video, null, job, VideoAssetClass.NORMALIZED_VIDEO,
        video.getNormalizedStorageKey(), DELETE_REASON, now);
    enqueue(video, null, job, VideoAssetClass.THUMBNAIL,
        video.getThumbnailKey(), DELETE_REASON, now);
    if (job != null && invalidatedToken != null) {
      String prefix = "academy-video/attempts/" + job.getJobId() + "/"
          + job.getAttemptCount() + "/" + invalidatedToken + "/";
      enqueue(video, null, job, VideoAssetClass.ATTEMPT_OUTPUT,
          prefix + "normalized.mp4", DELETE_REASON, now);
      enqueue(video, null, job, VideoAssetClass.ATTEMPT_OUTPUT,
          prefix + "thumbnail.jpg", DELETE_REASON, now);
    }
    for (AcademyVideoUploadSession session :
        sessionRepository.findByAcceptedVideo_VideoId(video.getVideoId())) {
      enqueue(video, session, job, VideoAssetClass.MULTIPART_UPLOAD,
          session.getMultipartUploadId(), DELETE_REASON, now);
      // This can dedupe separately from RAW_VIDEO. Both deletes are idempotent and the explicit
      // session identity is needed to prove multipart/staging ownership before final row removal.
      enqueue(video, session, job, VideoAssetClass.STAGED_OBJECT,
          session.getStagingKey(), DELETE_REASON, now);
    }
    cleanupRepository.flush();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CleanupClaim claimNext(String owner) {
    Long id = cleanupRepository.lockNextDueId().orElse(null);
    if (id == null) return null;
    AcademyVideoAssetCleanup cleanup =
        cleanupRepository.findForUpdateByCleanupId(id).orElse(null);
    if (cleanup == null) return null;
    LocalDateTime now = cleanupRepository.databaseNow();
    String token = UUID.randomUUID().toString();
    if (!cleanup.claim(owner, token, now, now.plusNanos(properties.getCleanupLeaseMs() * 1_000_000))) {
      return null;
    }
    AcademyVideoUploadSession session = cleanup.getSession();
    return new CleanupClaim(
        cleanup.getCleanupId(), token, cleanup.getAttemptCount(), cleanup.getAssetClass(),
        cleanup.getAssetKey(), session == null ? null : session.getStagingKey(),
        cleanup.getVideo() == null ? null : cleanup.getVideo().getVideoId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean complete(CleanupClaim claim) {
    AcademyVideoAssetCleanup cleanup =
        cleanupRepository.findForUpdateByCleanupId(claim.cleanupId()).orElse(null);
    if (cleanup == null || !cleanup.complete(claim.leaseToken())) return false;
    AcademyVideo video = cleanup.getVideo();
    AcademyVideoUploadSession session = cleanup.getSession();
    if (video != null
        && cleanup.getAssetClass() == VideoAssetClass.RAW_VIDEO
        && video.getActiveSource() == VideoActiveSource.NORMALIZED
        && video.getRawCleanupStatus() == VideoRawCleanupStatus.PENDING) {
      video.completeRawCleanup(cleanup.getAssetKey());
    }
    cleanupRepository.flush();
    if (video != null && video.getLifecycleStatus() == VideoLifecycleStatus.DELETING) {
      finalizeVideoIfReady(video.getVideoId());
    }
    if (session != null && session.getAcceptedVideo() == null) {
      finalizeTerminalSessionIfReady(session.getSessionId());
    }
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean retry(CleanupClaim claim, String safeErrorCode) {
    AcademyVideoAssetCleanup cleanup =
        cleanupRepository.findForUpdateByCleanupId(claim.cleanupId()).orElse(null);
    if (cleanup == null) return false;
    LocalDateTime now = cleanupRepository.databaseNow();
    long multiplier = 1L << Math.min(7, Math.max(0, cleanup.getAttemptCount() - 1));
    long delayMs = Math.min(3_600_000L, properties.getCleanupRetryBaseMs() * multiplier);
    return cleanup.retry(
        claim.leaseToken(), safeErrorCode, now.plusNanos(delayMs * 1_000_000));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reconcileDatabaseState() {
    int batchSize = properties.getCleanupReconciliationBatchSize();
    var page = PageRequest.of(0, batchSize);
    for (AcademyVideo video :
        videoRepository.findByLifecycleStatusOrderByVideoId(
            VideoLifecycleStatus.DELETING, page)) {
      AcademyVideo locked =
          videoRepository.findForUpdateByVideoId(video.getVideoId()).orElse(null);
      if (locked != null) {
        tombstoneLocked(locked);
        finalizeVideoIfReady(locked.getVideoId());
      }
    }
    LocalDateTime now = cleanupRepository.databaseNow();
    for (AcademyVideo video : videoRepository
        .findByLifecycleStatusAndActiveSourceAndRawCleanupStatusOrderByVideoId(
            VideoLifecycleStatus.READY, VideoActiveSource.NORMALIZED,
            VideoRawCleanupStatus.PENDING, page)) {
      AcademyVideoOptimizationJob job =
          jobRepository.findByVideo_VideoId(video.getVideoId()).orElse(null);
      enqueue(video, null, job, VideoAssetClass.RAW_VIDEO,
          video.getRawStorageKey(), "NORMALIZED_SOURCE_PUBLISHED", now);
    }
    for (AcademyVideoUploadSession session : sessionRepository
        .findByAcceptedVideoIsNullAndStateInOrderBySessionId(
            List.of(VideoUploadSessionState.ABORTED, VideoUploadSessionState.EXPIRED,
                VideoUploadSessionState.REJECTED), page)) {
      finalizeTerminalSessionIfReady(session.getSessionId());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void enqueueOrphan(VideoAssetClass assetClass, String key) {
    if (key == null || key.isBlank() || referenced(key)) return;
    enqueue(null, null, null, assetClass, key, "ORPHAN_RECONCILIATION",
        cleanupRepository.databaseNow());
  }

  private boolean referenced(String key) {
    return videoRepository.existsByRawStorageKey(key)
        || videoRepository.existsByNormalizedStorageKey(key)
        || videoRepository.existsByThumbnailKey(key)
        || sessionRepository.existsByStagingKey(key);
  }

  private void finalizeVideoIfReady(Long videoId) {
    if (cleanupRepository.existsByVideo_VideoIdAndStateNot(
        videoId, VideoAssetCleanupState.DONE)) {
      return;
    }
    AcademyVideo video = videoRepository.findForUpdateByVideoId(videoId).orElse(null);
    if (video == null || video.getLifecycleStatus() != VideoLifecycleStatus.DELETING) return;
    List<AcademyVideoAssetCleanup> cleanupRows =
        cleanupRepository.findByVideo_VideoId(videoId);
    if (cleanupRows.isEmpty()) return;
    cleanupRepository.deleteAllInBatch(cleanupRows);
    cleanupRepository.flush();
    for (AcademyVideoUploadSession session :
        sessionRepository.findByAcceptedVideo_VideoId(videoId)) {
      sessionRepository.delete(session);
    }
    sessionRepository.flush();
    jobRepository.findByVideo_VideoId(videoId).ifPresent(jobRepository::delete);
    jobRepository.flush();
    videoRepository.delete(video);
    videoRepository.flush();
  }

  private void finalizeTerminalSessionIfReady(String sessionId) {
    List<AcademyVideoAssetCleanup> rows =
        cleanupRepository.findBySession_SessionId(sessionId);
    if (rows.isEmpty()
        || cleanupRepository.existsBySession_SessionIdAndStateNot(
            sessionId, VideoAssetCleanupState.DONE)) {
      return;
    }
    cleanupRepository.deleteAllInBatch(rows);
    cleanupRepository.flush();
    sessionRepository.deleteById(sessionId);
    sessionRepository.flush();
  }

  private void enqueue(
      AcademyVideo video,
      AcademyVideoUploadSession session,
      AcademyVideoOptimizationJob job,
      VideoAssetClass assetClass,
      String key,
      String reason,
      LocalDateTime now) {
    if (key == null || key.isBlank()) return;
    String dedupe = sha256(assetClass.name() + "\n" + key + "\n" + reason);
    cleanupRepository.insertPendingIgnoreDuplicate(
        dedupe, session == null ? null : session.getSessionId(),
        video == null ? null : video.getVideoId(), job == null ? null : job.getJobId(),
        assetClass.name(), key, reason, now);
  }

  private static boolean isTerminal(VideoUploadSessionState state) {
    return state == VideoUploadSessionState.ABORTED
        || state == VideoUploadSessionState.EXPIRED
        || state == VideoUploadSessionState.REJECTED;
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public record CleanupClaim(
      Long cleanupId,
      String leaseToken,
      int attempt,
      VideoAssetClass assetClass,
      String assetKey,
      String stagingKey,
      Long videoId) {}
}
