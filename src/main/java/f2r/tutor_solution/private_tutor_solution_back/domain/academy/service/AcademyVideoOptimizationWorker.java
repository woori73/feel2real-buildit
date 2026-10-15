package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoOptimizationTransactions.OptimizationClaim;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoOptimizationTransactions.PublishedAssets;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoStorageException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoWorkerStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.ProcessCancelledException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.ProcessDeadlineExceededException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.ProcessStalledException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.TranscodeResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoProbe;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscodeException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Durable same-EC2 optimizer. All durable state changes are delegated to short transactions. */
@Slf4j
@Service
@ConditionalOnProperty(
    name = "app.video.optimization-enabled", havingValue = "true", matchIfMissing = true)
public class AcademyVideoOptimizationWorker {
  private final AcademyVideoOptimizationTransactions transactions;
  private final AcademyVideoOptimizationCancellation cancellation;
  private final ObjectProvider<AcademyVideoWorkerStorage> storageProvider;
  private final VideoTranscoder transcoder;
  private final AcademyVideoUploadProperties properties;
  private final Executor conversionExecutor;
  private final AcademyVideoMetrics metrics;
  private final Semaphore localSlot = new Semaphore(1);
  private final String owner = UUID.randomUUID().toString();

  public AcademyVideoOptimizationWorker(
      AcademyVideoOptimizationTransactions transactions,
      AcademyVideoOptimizationCancellation cancellation,
      ObjectProvider<AcademyVideoWorkerStorage> storageProvider,
      VideoTranscoder transcoder,
      AcademyVideoUploadProperties properties,
      @Qualifier("academyVideoConversionExecutor") Executor conversionExecutor,
      AcademyVideoMetrics metrics) {
    this.transactions = transactions;
    this.cancellation = cancellation;
    this.storageProvider = storageProvider;
    this.transcoder = transcoder;
    this.properties = properties;
    this.conversionExecutor = conversionExecutor;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "#{@academyVideoUploadProperties.optimizationPollMs}")
  public void poll() {
    if (workerStorage() == null) return;
    // Local capacity is acquired before the REQUIRES_NEW database claim.
    if (!localSlot.tryAcquire()) return;
    OptimizationClaim claim;
    try {
      claim = transactions.claimNext(owner);
      if (claim == null) {
        localSlot.release();
        return;
      }
      conversionExecutor.execute(() -> runClaim(claim));
    } catch (RejectedExecutionException e) {
      localSlot.release();
      throw e;
    } catch (RuntimeException e) {
      localSlot.release();
      log.warn("academy video claim failed safely", e);
    }
  }

  @Scheduled(fixedDelayString = "#{@academyVideoUploadProperties.optimizationHeartbeatMs}")
  public void heartbeatActiveAttempts() {
    cancellation.snapshot().forEach((jobId, active) -> {
      if (!transactions.heartbeat(
          jobId, active.token(), active.mediaProgress().getAndSet(false))) {
        cancellation.signal(jobId, active.token());
      }
    });
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverAfterRestart() {
    poll();
  }

  private void runClaim(OptimizationClaim claim) {
    long startedNanos = System.nanoTime();
    String outcome = "FAILED";
    String reason = "UNEXPECTED";
    AcademyVideoWorkerStorage storage = workerStorage();
    if (storage == null) {
      transactions.retryableFailure(
          claim.jobId(), claim.leaseToken(), "WORKER_STORAGE_UNAVAILABLE",
          "worker storage unavailable");
      recordAttempt(claim, "RETRY_WAIT", "WORKER_STORAGE_UNAVAILABLE", startedNanos, 0);
      localSlot.release();
      return;
    }
    OptimizationAttemptBudget budget = OptimizationAttemptBudget.start();
    AtomicLong lastMediaProgressEpochMs = new AtomicLong();
    Path workspace = null;
    String normalizedKey =
        storage.attemptKey(claim.jobId(), claim.attempt(), claim.leaseToken(), "normalized.mp4");
    String thumbnailKey =
        storage.attemptKey(claim.jobId(), claim.attempt(), claim.leaseToken(), "thumbnail.jpg");
    boolean uploaded = false;
    boolean published = false;
    try (AcademyVideoOptimizationCancellation.Handle handle =
             cancellation.register(claim.jobId(), claim.leaseToken())) {
      workspace = workspace(claim);
      Path input = workspace.resolve("raw-input");
      storage.materialize(claim.rawStorageKey(), input, budget.remaining());
      VideoProbe probe = transcoder.probe(input, budget.remaining(), handle::cancelled);
      TranscodeResult result = transcoder.transcodeAttempt(
          input, probe, workspace, budget::remaining, handle::cancelled,
          () -> {
            handle.recordMediaProgress();
            lastMediaProgressEpochMs.set(System.currentTimeMillis());
          });
      if (result.sizeBytes() > properties.getMaxOutputBytes()) {
        throw new VideoTranscodeException("VIDEO_OUTPUT_TOO_LARGE");
      }
      uploaded = true;
      storage.upload(normalizedKey, result.mp4(), "video/mp4", budget.remaining());
      storage.upload(thumbnailKey, result.thumbnail(), "image/jpeg", budget.remaining());
      AcademyVideoWorkerStorage.StoredObject normalized =
          storage.inspectWorkerObject(normalizedKey, budget.remaining());
      AcademyVideoWorkerStorage.StoredObject thumbnail =
          storage.inspectWorkerObject(thumbnailKey, budget.remaining());
      if (normalized.sizeBytes() < 1
          || normalized.sizeBytes() > properties.getMaxOutputBytes()
          || thumbnail.sizeBytes() < 1) {
        throw new VideoTranscodeException("VIDEO_OUTPUT_VALIDATION_FAILED");
      }
      published = transactions.publish(
          claim,
          new PublishedAssets(
              normalizedKey, normalized.sizeBytes(), thumbnailKey,
              Math.round(probe.durationSec() * 1_000), result.width(), result.height()),
          budget);
      if (!published) {
        cancellation.signal(claim.jobId(), claim.leaseToken());
        metrics.sourcePublishConflict();
        outcome = "CONFLICT";
        reason = "SOURCE_PUBLISH_CONFLICT";
      } else {
        outcome = "SUCCEEDED";
        reason = "NORMALIZED_PUBLISHED";
      }
    } catch (AcademyVideoStorageException e) {
      if ("OBJECT_MISSING".equals(e.getMessage())) {
        transactions.nonRetryableFailure(
            claim.jobId(), claim.leaseToken(), "RAW_OBJECT_MISSING");
        outcome = "CANCELLED";
        reason = "RAW_OBJECT_MISSING";
      } else {
        reason = safeCode(e);
        transactions.retryableFailure(
            claim.jobId(), claim.leaseToken(), reason, "storage operation failed");
        outcome = retryOutcome(claim);
      }
    } catch (ProcessCancelledException e) {
      // A stale token must not perform any terminal transition.
      reason = safeCode(e);
      transactions.retryableFailure(
          claim.jobId(), claim.leaseToken(), reason, "attempt cancelled");
      outcome = "CANCELLED";
    } catch (ProcessDeadlineExceededException e) {
      reason = "PROCESS_TIMEOUT";
      transactions.retryableFailure(
          claim.jobId(), claim.leaseToken(), reason, "process budget exceeded");
      metrics.jobTimeout();
      outcome = retryOutcome(claim);
    } catch (ProcessStalledException e) {
      reason = "PROCESS_STALLED";
      transactions.retryableFailure(
          claim.jobId(), claim.leaseToken(), reason, "process media progress stalled");
      metrics.jobStall();
      outcome = retryOutcome(claim);
    } catch (IOException e) {
      transactions.retryableFailure(
          claim.jobId(), claim.leaseToken(), "STORAGE_IO_TRANSIENT", "I/O operation failed");
      outcome = retryOutcome(claim);
      reason = "STORAGE_IO_TRANSIENT";
    } catch (RuntimeException e) {
      reason = safeCode(e);
      transactions.retryableFailure(
          claim.jobId(), claim.leaseToken(), reason, "optimization failed");
      outcome = retryOutcome(claim);
    } finally {
      if (uploaded && !published) {
        try {
          transactions.enqueueAttemptCleanup(
              claim, normalizedKey, thumbnailKey, "ATTEMPT_NOT_PUBLISHED");
        } catch (RuntimeException cleanupFailure) {
          log.error("failed to persist attempt output cleanup for job {}", claim.jobId(),
              cleanupFailure);
        } finally {
          bestEffortDelete(storage, normalizedKey);
          bestEffortDelete(storage, thumbnailKey);
        }
      }
      deleteWorkspace(workspace);
      recordAttempt(claim, outcome, reason, startedNanos, lastMediaProgressEpochMs.get());
      localSlot.release();
    }
  }

  private static void bestEffortDelete(AcademyVideoWorkerStorage storage, String key) {
    try {
      storage.deleteWorkerObject(key);
    } catch (RuntimeException ignored) {
      // The durable deterministic cleanup identity remains available to G004 reconciliation.
    }
  }

  private AcademyVideoWorkerStorage workerStorage() {
    return storageProvider.getIfAvailable();
  }

  private Path workspace(OptimizationClaim claim) throws IOException {
    Path root = Path.of(properties.getOptimizationTempDir()).toAbsolutePath().normalize();
    Path path = root.resolve(claim.jobId().toString())
        .resolve(Integer.toString(claim.attempt()))
        .resolve(claim.leaseToken()).normalize();
    if (!path.startsWith(root)) throw new IOException("unsafe optimization workspace");
    Files.createDirectories(path);
    return path;
  }

  private static String safeCode(Throwable failure) {
    String value = failure.getMessage();
    if (value == null || !value.matches("[A-Za-z0-9_\\-]{1,80}")) {
      value = failure.getClass().getSimpleName();
    }
    value = value.toUpperCase();
    return value.substring(0, Math.min(80, value.length()));
  }

  private static String retryOutcome(OptimizationClaim claim) {
    return claim.attempt() == 1 ? "RETRY_WAIT" : "FAILED";
  }

  private void recordAttempt(
      OptimizationClaim claim, String outcome, String reason, long startedNanos,
      long lastMediaProgressEpochMs) {
    long elapsedNanos = Math.max(0, System.nanoTime() - startedNanos);
    metrics.jobAttempt(outcome, reason, elapsedNanos);
    log.info("academy_video_attempt jobId={} videoId={} attempt={} stage=terminal leaseOwner={} leaseTokenHash={} lastMediaProgressEpochMs={} elapsedMs={} outcome={} reason={}",
        claim.jobId(), claim.videoId(), claim.attempt(), owner,
        leaseTokenHash(claim.leaseToken()), lastMediaProgressEpochMs,
        TimeUnit.NANOSECONDS.toMillis(elapsedNanos), outcome, reason);
  }

  static String leaseTokenHash(String leaseToken) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(leaseToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static void deleteWorkspace(Path workspace) {
    if (workspace == null || !Files.exists(workspace)) return;
    try (var paths = Files.walk(workspace)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (IOException ignored) {
    }
  }
}
