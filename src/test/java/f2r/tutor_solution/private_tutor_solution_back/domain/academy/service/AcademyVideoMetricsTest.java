package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetCleanupState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoOptimizationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoAssetCleanupRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoUploadSessionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AcademyVideoMetricsTest {

  @Test
  @DisplayName("given durable state when gauges are observed then repository counts are exposed")
  void gaugesReflectDurableRepositoryState() {
    // given
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AcademyVideoUploadSessionRepository sessions = mock(AcademyVideoUploadSessionRepository.class);
    AcademyVideoOptimizationJobRepository jobs = mock(AcademyVideoOptimizationJobRepository.class);
    AcademyVideoAssetCleanupRepository cleanups = mock(AcademyVideoAssetCleanupRepository.class);
    when(sessions.countByStateIn(List.of(
        VideoUploadSessionState.INITIATED, VideoUploadSessionState.COMPLETING))).thenReturn(3L);
    when(jobs.countByStatus(VideoOptimizationStatus.PROCESSING)).thenReturn(2L);
    when(cleanups.countByStateNot(VideoAssetCleanupState.DONE)).thenReturn(4L);

    // when
    new AcademyVideoMetrics(registry, sessions, jobs, cleanups);

    // then
    assertThat(registry.get("academy_video_multipart_incomplete").gauge().value()).isEqualTo(3);
    assertThat(registry.get("academy_video_jobs").tag("status", "PROCESSING").gauge().value())
        .isEqualTo(2);
    assertThat(registry.get("academy_video_cleanup_pending").gauge().value()).isEqualTo(4);
  }

  @Test
  @DisplayName("given terminal outcomes when recorded then bounded operational counters are emitted")
  void recordsBoundedOperationalCounters() {
    // given
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AcademyVideoMetrics metrics = new AcademyVideoMetrics(
        registry,
        mock(AcademyVideoUploadSessionRepository.class),
        mock(AcademyVideoOptimizationJobRepository.class),
        mock(AcademyVideoAssetCleanupRepository.class));

    // when
    metrics.sessionTerminal(VideoUploadSessionState.EXPIRED, Duration.ofSeconds(7));
    metrics.jobAttempt("retry wait", "PROCESS_TIMEOUT", Duration.ofSeconds(2).toNanos());
    metrics.jobTimeout();
    metrics.jobStall();
    metrics.leaseReclaimed();
    metrics.sourcePublishConflict();
    metrics.cleanupFailure("attempt output");

    // then
    assertThat(registry.get("academy_video_upload_sessions_total")
        .tag("outcome", "EXPIRED").counter().count()).isEqualTo(1);
    assertThat(registry.get("academy_video_upload_expired_total").counter().count()).isEqualTo(1);
    assertThat(registry.get("academy_video_upload_duration_seconds")
        .tag("outcome", "EXPIRED").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS))
        .isEqualTo(7);
    assertThat(registry.get("academy_video_job_attempts_total")
        .tag("outcome", "RETRY_WAIT").tag("reason", "PROCESS_TIMEOUT")
        .counter().count()).isEqualTo(1);
    assertThat(registry.get("academy_video_job_timeouts_total").counter().count()).isEqualTo(1);
    assertThat(registry.get("academy_video_job_stalls_total").counter().count()).isEqualTo(1);
    assertThat(registry.get("academy_video_job_lease_reclaims_total").counter().count())
        .isEqualTo(1);
    assertThat(registry.get("academy_video_source_publish_conflicts_total").counter().count())
        .isEqualTo(1);
    assertThat(registry.get("academy_video_cleanup_failures_total")
        .tag("asset_type", "ATTEMPT_OUTPUT").counter().count()).isEqualTo(1);
  }
}
