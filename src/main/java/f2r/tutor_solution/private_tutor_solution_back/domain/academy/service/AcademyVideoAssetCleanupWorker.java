package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoCleanupTransactions.CleanupClaim;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoStorageException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoUploadStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoWorkerStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-outcome-bounded external cleanup worker; scheduler threads only dispatch work. */
@Slf4j
@Service
public class AcademyVideoAssetCleanupWorker {
  private final AcademyVideoCleanupTransactions transactions;
  private final ObjectProvider<AcademyVideoWorkerStorage> workerStorageProvider;
  private final ObjectProvider<AcademyVideoUploadStorage> uploadStorageProvider;
  private final AcademyVideoUploadProperties properties;
  private final Executor cleanupExecutor;
  private final AcademyVideoMetrics metrics;
  private final AtomicBoolean running = new AtomicBoolean();
  private final String owner = UUID.randomUUID().toString();

  public AcademyVideoAssetCleanupWorker(
      AcademyVideoCleanupTransactions transactions,
      ObjectProvider<AcademyVideoWorkerStorage> workerStorageProvider,
      ObjectProvider<AcademyVideoUploadStorage> uploadStorageProvider,
      AcademyVideoUploadProperties properties,
      @Qualifier("academyVideoCleanupExecutor") Executor cleanupExecutor,
      AcademyVideoMetrics metrics) {
    this.transactions = transactions;
    this.workerStorageProvider = workerStorageProvider;
    this.uploadStorageProvider = uploadStorageProvider;
    this.properties = properties;
    this.cleanupExecutor = cleanupExecutor;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "#{@academyVideoUploadProperties.cleanupPollMs}",
      scheduler = "academyVideoCleanupScheduler")
  public void poll() {
    if (!running.compareAndSet(false, true)) return;
    try {
      cleanupExecutor.execute(this::runOne);
    } catch (RejectedExecutionException e) {
      running.set(false);
    }
  }

  @Scheduled(fixedDelayString = "#{@academyVideoUploadProperties.cleanupOrphanMinAgeMs}",
      scheduler = "academyVideoCleanupScheduler")
  public void reconcile() {
    transactions.reconcileDatabaseState();
    AcademyVideoWorkerStorage storage = workerStorage();
    if (storage == null) return;
    Instant cutoff = Instant.now().minusMillis(properties.getCleanupOrphanMinAgeMs());
    storage.listWorkerObjects("academy-video/attempts/", cutoff,
            properties.getCleanupReconciliationBatchSize())
        .forEach(found -> transactions.enqueueOrphan(
            VideoAssetClass.ATTEMPT_OUTPUT, found.key()));
    storage.listWorkerObjects("staging/", cutoff,
            properties.getCleanupReconciliationBatchSize())
        .forEach(found -> transactions.enqueueOrphan(
            VideoAssetClass.STAGED_OBJECT, found.key()));
  }

  private void runOne() {
    long startedNanos = System.nanoTime();
    CleanupClaim claim = null;
    String outcome = "IDLE";
    String reason = "NO_DUE_CLEANUP";
    try {
      claim = transactions.claimNext(owner);
      if (claim == null) return;
      try {
        deleteAndConfirm(claim);
        transactions.complete(claim);
        outcome = "SUCCEEDED";
        reason = "DELETE_CONFIRMED";
      } catch (RuntimeException failure) {
        reason = safeCode(failure);
        outcome = "RETRY_WAIT";
        transactions.retry(claim, reason);
        metrics.cleanupFailure(claim.assetClass().name());
      }
    } finally {
      if (claim != null) {
        log.info("academy_video_cleanup cleanupId={} assetType={} attempt={} stage=terminal elapsedMs={} outcome={} reason={}",
            claim.cleanupId(), claim.assetClass(), claim.attempt(),
            TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedNanos)),
            outcome, reason);
      }
      running.set(false);
    }
  }

  private void deleteAndConfirm(CleanupClaim claim) {
    if (claim.assetClass() == VideoAssetClass.MULTIPART_UPLOAD) {
      AcademyVideoUploadStorage upload = uploadStorage();
      if (upload == null || claim.stagingKey() == null) {
        throw new AcademyVideoStorageException("UPLOAD_STORAGE_UNAVAILABLE");
      }
      // Abort is idempotent: an already absent multipart upload is success.
      upload.abortForCleanup(claim.stagingKey(), claim.assetKey());
      return;
    }
    AcademyVideoWorkerStorage storage = workerStorage();
    if (storage == null) {
      throw new AcademyVideoStorageException("WORKER_STORAGE_UNAVAILABLE");
    }
    storage.deleteWorkerObject(claim.assetKey());
    try {
      storage.inspectWorkerObject(claim.assetKey(), Duration.ofSeconds(5));
      throw new AcademyVideoStorageException("OBJECT_STILL_PRESENT");
    } catch (AcademyVideoStorageException absent) {
      if (!"OBJECT_MISSING".equals(absent.getMessage())) throw absent;
    }
  }

  private AcademyVideoWorkerStorage workerStorage() {
    return workerStorageProvider.getIfAvailable();
  }

  private AcademyVideoUploadStorage uploadStorage() {
    return uploadStorageProvider.getIfAvailable();
  }

  private static String safeCode(Throwable failure) {
    String value = failure.getMessage();
    if (value == null || value.isBlank()) value = failure.getClass().getSimpleName();
    value = value.replaceAll("[^A-Za-z0-9_\\-]", "_").toUpperCase();
    return value.substring(0, Math.min(80, value.length()));
  }
}
