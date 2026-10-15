package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Academy video cleanup transaction — tombstone, bounded reconcile, scheduler isolation")
class AcademyVideoCleanupTransactionsTest {

  @Test
  void givenActiveVideoAndSession_whenTombstoned_thenCancelSignalsAndEveryKnownAssetIsAtomicEnqueued() {
    // given
    Fixtures f = fixtures();
    AcademyVideo video = mock(AcademyVideo.class);
    AcademyVideoOptimizationJob job = mock(AcademyVideoOptimizationJob.class);
    AcademyVideoUploadSession session = mock(AcademyVideoUploadSession.class);
    when(video.getVideoId()).thenReturn(7L);
    when(video.getRawStorageKey()).thenReturn("raw/key");
    when(video.getNormalizedStorageKey()).thenReturn("normalized/key");
    when(video.getThumbnailKey()).thenReturn("thumbnail/key");
    when(job.getJobId()).thenReturn(11L);
    when(job.getAttemptCount()).thenReturn(2);
    when(job.cancelForDeletion(any())).thenReturn("old-lease");
    when(session.getSessionId()).thenReturn("session-id");
    when(session.getMultipartUploadId()).thenReturn("upload-id");
    when(session.getStagingKey()).thenReturn("staging/key");
    when(f.jobs.findForUpdateByVideoId(7L)).thenReturn(java.util.Optional.of(job));
    when(f.sessions.findByAcceptedVideo_VideoId(7L)).thenReturn(List.of(session));

    // when
    f.transactions.tombstoneLocked(video);

    // then
    verify(video).markDeleting();
    verify(f.cancellation).signal(11L, "old-lease");
    verify(f.cleanups, times(7)).insertPendingIgnoreDuplicate(
        anyString(), any(), eq(7L), eq(11L), anyString(), anyString(),
        eq("VIDEO_DELETING"), any());
    verify(f.cleanups).flush();
  }

  @Test
  void givenConfiguredBatch_whenReconcileRuns_thenOnlyIndexedBoundedQueriesAreUsed() {
    // given
    Fixtures f = fixtures();
    f.properties.setCleanupReconciliationBatchSize(17);

    // when
    f.transactions.reconcileDatabaseState();

    // then
    verify(f.videos).findByLifecycleStatusOrderByVideoId(
        eq(VideoLifecycleStatus.DELETING), argThat(page -> page.getPageSize() == 17));
    verify(f.videos).findByLifecycleStatusAndActiveSourceAndRawCleanupStatusOrderByVideoId(
        eq(VideoLifecycleStatus.READY), eq(VideoActiveSource.NORMALIZED),
        eq(VideoRawCleanupStatus.PENDING), argThat(page -> page.getPageSize() == 17));
    verify(f.sessions).findByAcceptedVideoIsNullAndStateInOrderBySessionId(
        anyCollection(), argThat(page -> page.getPageSize() == 17));
    verify(f.videos, never()).findAll();
    verify(f.sessions, never()).findAll();
  }

  @Test
  void cleanupSchedulesDeclareDedicatedSchedulerSoOptimizationHeartbeatCannotBeStarved() throws Exception {
    // given / when
    Scheduled poll = AcademyVideoAssetCleanupWorker.class.getMethod("poll")
        .getAnnotation(Scheduled.class);
    Scheduled reconcile = AcademyVideoAssetCleanupWorker.class.getMethod("reconcile")
        .getAnnotation(Scheduled.class);

    // then
    assertThat(poll.scheduler()).isEqualTo("academyVideoCleanupScheduler");
    assertThat(reconcile.scheduler()).isEqualTo("academyVideoCleanupScheduler");
  }

  private static Fixtures fixtures() {
    AcademyVideoAssetCleanupRepository cleanups = mock(AcademyVideoAssetCleanupRepository.class);
    AcademyVideoRepository videos = mock(AcademyVideoRepository.class);
    AcademyVideoOptimizationJobRepository jobs = mock(AcademyVideoOptimizationJobRepository.class);
    AcademyVideoUploadSessionRepository sessions = mock(AcademyVideoUploadSessionRepository.class);
    AcademyVideoOptimizationCancellation cancellation = mock(AcademyVideoOptimizationCancellation.class);
    AcademyVideoUploadProperties properties = new AcademyVideoUploadProperties();
    when(cleanups.databaseNow()).thenReturn(LocalDateTime.now());
    AcademyVideoCleanupTransactions transactions = new AcademyVideoCleanupTransactions(
        cleanups, videos, jobs, sessions, cancellation, properties);
    return new Fixtures(cleanups, videos, jobs, sessions, cancellation, properties, transactions);
  }

  private record Fixtures(
      AcademyVideoAssetCleanupRepository cleanups,
      AcademyVideoRepository videos,
      AcademyVideoOptimizationJobRepository jobs,
      AcademyVideoUploadSessionRepository sessions,
      AcademyVideoOptimizationCancellation cancellation,
      AcademyVideoUploadProperties properties,
      AcademyVideoCleanupTransactions transactions) {}
}
