package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoAssetCleanup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoOptimizationJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoActiveSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoAssetCleanupRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/** Short, independently committed job lease and publication transactions. */
@Service
@RequiredArgsConstructor
public class AcademyVideoOptimizationTransactions {
  static final long LEASE_MICROS = 90_000_000L;

  private final AcademyVideoOptimizationJobRepository jobRepository;
  private final AcademyVideoRepository videoRepository;
  private final AcademyVideoAssetCleanupRepository cleanupRepository;
  private final PlatformTransactionManager transactionManager;
  private final AcademyVideoMetrics metrics;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public OptimizationClaim claimNext(String owner) {
    Long expiredId = jobRepository.lockNextExpiredId().orElse(null);
    if (expiredId != null) {
      AcademyVideoOptimizationJob expired = jobRepository.findById(expiredId).orElse(null);
      if (expired != null) {
        expired.recoverExpired(jobRepository.databaseNow());
        AcademyVideoMetrics.afterCommit(metrics::leaseReclaimed);
      }
      return null;
    }
    Long jobId = jobRepository.lockNextDueClaimId().orElse(null);
    if (jobId == null) return null;
    AcademyVideoOptimizationJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null) return null;
    LocalDateTime now = jobRepository.databaseNow();
    AcademyVideo video = job.getVideo();
    if (video.getLifecycleStatus() != VideoLifecycleStatus.READY
        || video.getActiveSource() != VideoActiveSource.RAW
        || video.getRawStorageKey() == null) {
      job.cancelBeforeClaim("RAW_SOURCE_NOT_AVAILABLE", now);
      return null;
    }
    String token = UUID.randomUUID().toString();
    if (!job.claim(owner, token, now, now.plusSeconds(90))) return null;
    return new OptimizationClaim(
        job.getJobId(), video.getVideoId(), job.getAttemptCount(), token,
        video.getSourceVersion(), video.getRawStorageKey());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean heartbeat(Long jobId, String token, boolean mediaProgress) {
    return jobRepository.heartbeat(jobId, token, LEASE_MICROS, mediaProgress) == 1;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean retryableFailure(
      Long jobId, String token, String code, String diagnostic) {
    AcademyVideoOptimizationJob job =
        jobRepository.findForUpdateByJobId(jobId).orElse(null);
    if (job == null) return false;
    LocalDateTime now = jobRepository.databaseNow();
    return job.getAttemptCount() == 1
        ? job.retry(token, code, diagnostic, now)
        : job.fail(token, code, diagnostic, now);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean nonRetryableFailure(Long jobId, String token, String code) {
    AcademyVideoOptimizationJob job =
        jobRepository.findForUpdateByJobId(jobId).orElse(null);
    return job != null && job.cancel(token, code, jobRepository.databaseNow());
  }

  public boolean publish(
      OptimizationClaim claim,
      PublishedAssets assets,
      OptimizationAttemptBudget budget) {
    Duration remaining = budget.remaining();
    TransactionTemplate publication = new TransactionTemplate(transactionManager);
    publication.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    publication.setTimeout(timeoutSeconds(remaining));
    Boolean result = publication.execute(status -> publishWithinTransaction(claim, assets, budget));
    return Boolean.TRUE.equals(result);
  }

  private boolean publishWithinTransaction(
      OptimizationClaim claim,
      PublishedAssets assets,
      OptimizationAttemptBudget budget) {
    // The transaction timeout bounds lock/query/commit work. The synchronization is the exact
    // monotonic fence: a commit reaching elapsed >=600s throws and rolls the source switch back.
    budget.remaining();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void beforeCommit(boolean readOnly) {
        budget.remaining();
      }
    });
    AcademyVideo video =
        videoRepository.findForUpdateByVideoId(claim.videoId()).orElse(null);
    if (video == null) return false;
    AcademyVideoOptimizationJob job =
        jobRepository.findForUpdateByJobId(claim.jobId()).orElse(null);
    if (job == null
        || !job.leaseMatches(claim.leaseToken())
        || !job.getVideo().getVideoId().equals(video.getVideoId())
        || !video.publishNormalized(
            claim.sourceVersion(), assets.normalizedKey(), assets.normalizedBytes(),
            assets.thumbnailKey(), assets.durationMs(), assets.width(), assets.height())) {
      return false;
    }
    LocalDateTime now = jobRepository.databaseNow();
    if (!job.succeed(claim.leaseToken(), now)) return false;
    enqueue(
        video, job, VideoAssetClass.RAW_VIDEO, video.getRawStorageKey(),
        "NORMALIZED_SOURCE_PUBLISHED", now);
    cleanupRepository.flush();
    return true;
  }

  private static int timeoutSeconds(Duration remaining) {
    long nanos = remaining.toNanos();
    long seconds = Math.max(1, Math.floorDiv(nanos + 999_999_999L, 1_000_000_000L));
    return (int) Math.min(Integer.MAX_VALUE, seconds);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void enqueueAttemptCleanup(
      OptimizationClaim claim, String normalizedKey, String thumbnailKey, String reason) {
    AcademyVideoOptimizationJob job = jobRepository.findById(claim.jobId()).orElse(null);
    AcademyVideo video = videoRepository.findById(claim.videoId()).orElse(null);
    LocalDateTime now = jobRepository.databaseNow();
    enqueue(video, job, VideoAssetClass.ATTEMPT_OUTPUT, normalizedKey, reason, now);
    enqueue(video, job, VideoAssetClass.ATTEMPT_OUTPUT, thumbnailKey, reason, now);
  }

  private void enqueue(
      AcademyVideo video,
      AcademyVideoOptimizationJob job,
      VideoAssetClass assetClass,
      String key,
      String reason,
      LocalDateTime now) {
    if (key == null) return;
    String dedupe = sha256(assetClass.name() + "\n" + key + "\n" + reason);
    cleanupRepository.insertPendingIgnoreDuplicate(
        dedupe, null, video == null ? null : video.getVideoId(),
        job == null ? null : job.getJobId(), assetClass.name(), key, reason, now);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public record OptimizationClaim(
      Long jobId,
      Long videoId,
      int attempt,
      String leaseToken,
      long sourceVersion,
      String rawStorageKey) {
    public String attemptPrefix() {
      return "academy-video/attempts/" + jobId + "/" + attempt + "/" + leaseToken;
    }
  }

  public record PublishedAssets(
      String normalizedKey,
      long normalizedBytes,
      String thumbnailKey,
      long durationMs,
      int width,
      int height) {}
}
