package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetCleanupState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoOptimizationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoAssetCleanupRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoUploadSessionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Low-cardinality metrics only; object identities and capabilities never become tags. */
@Component
public class AcademyVideoMetrics {
  private static final List<VideoUploadSessionState> INCOMPLETE = List.of(
      VideoUploadSessionState.INITIATED, VideoUploadSessionState.COMPLETING);

  private final MeterRegistry registry;

  public AcademyVideoMetrics(
      MeterRegistry registry,
      AcademyVideoUploadSessionRepository sessions,
      AcademyVideoOptimizationJobRepository jobs,
      AcademyVideoAssetCleanupRepository cleanups) {
    this.registry = registry;
    Gauge.builder("academy_video_multipart_incomplete", sessions,
            repository -> repository.countByStateIn(INCOMPLETE))
        .description("Current non-terminal Academy video multipart sessions")
        .register(registry);
    for (VideoOptimizationStatus status : VideoOptimizationStatus.values()) {
      Gauge.builder("academy_video_jobs", jobs, repository -> repository.countByStatus(status))
          .tag("status", status.name())
          .description("Current Academy video optimization jobs by durable status")
          .register(registry);
    }
    Gauge.builder("academy_video_cleanup_pending", cleanups,
            repository -> repository.countByStateNot(VideoAssetCleanupState.DONE))
        .description("Current Academy video cleanup rows not yet confirmed done")
        .register(registry);
  }

  public void sessionTerminal(VideoUploadSessionState outcome, Duration elapsed) {
    registry.counter("academy_video_upload_sessions_total", "outcome", outcome.name()).increment();
    if (outcome == VideoUploadSessionState.EXPIRED) {
      registry.counter("academy_video_upload_expired_total").increment();
    }
    Timer.builder("academy_video_upload_duration_seconds")
        .tag("outcome", outcome.name())
        .register(registry)
        .record(Math.max(0, elapsed.toNanos()), TimeUnit.NANOSECONDS);
  }

  public void jobAttempt(String outcome, String reason, long elapsedNanos) {
    Tags tags = Tags.of("outcome", safeTag(outcome), "reason", safeTag(reason));
    registry.counter("academy_video_job_attempts_total", tags).increment();
    Timer.builder("academy_video_job_duration_seconds")
        .tag("stage", "attempt")
        .register(registry)
        .record(Math.max(0, elapsedNanos), TimeUnit.NANOSECONDS);
  }

  public void jobStall() { registry.counter("academy_video_job_stalls_total").increment(); }
  public void jobTimeout() { registry.counter("academy_video_job_timeouts_total").increment(); }
  public void leaseReclaimed() {
    registry.counter("academy_video_job_lease_reclaims_total").increment();
  }
  public void sourcePublishConflict() {
    registry.counter("academy_video_source_publish_conflicts_total").increment();
  }
  public void cleanupFailure(String assetType) {
    registry.counter("academy_video_cleanup_failures_total", "asset_type", safeTag(assetType))
        .increment();
  }

  public static void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { action.run(); }
    });
  }

  private static String safeTag(String value) {
    if (value == null || value.isBlank()) return "UNKNOWN";
    String safe = value.replaceAll("[^A-Za-z0-9_\\-]", "_").toUpperCase();
    return safe.substring(0, Math.min(80, safe.length()));
  }
}
