package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoOptimizationTransactions.OptimizationClaim;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoStorageException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoWorkerStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.DefaultProcessRunner;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.ProcessCancelledException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.TranscodeResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoProbe;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AcademyVideoOptimizationWorkerTest {
  @TempDir Path temp;

  @Test
  void givenLeaseToken_whenHashedForTerminalLog_thenHashIsBoundedAndTokenIsNotExposed() {
    String hash = AcademyVideoOptimizationWorker.leaseTokenHash(claim().leaseToken());

    assertThat(hash)
        .isEqualTo("7ac1b8d7010bb6cd")
        .hasSize(16)
        .doesNotContain(claim().leaseToken());
  }

  @Test
  void givenObservedMediaProgress_whenAttemptTerminates_thenSafeProgressContextIsLogged()
      throws Exception {
    AcademyVideoOptimizationTransactions transactions =
        mock(AcademyVideoOptimizationTransactions.class);
    when(transactions.claimNext(anyString())).thenReturn(claim());
    when(transactions.publish(any(), any(), any())).thenReturn(true);
    VideoTranscoder transcoder = successfulTranscoder();
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(5).run();
      Path mp4 = Files.writeString(temp.resolve("progress-output.mp4"), "mp4");
      Path thumbnail = Files.writeString(temp.resolve("progress-thumbnail.jpg"), "jpg");
      return new TranscodeResult(mp4, thumbnail, 720, 1280, 2, 3);
    }).when(transcoder).transcodeAttempt(any(), any(), any(), any(), any(), any());
    AcademyVideoOptimizationWorker worker = worker(
        transactions, new AcademyVideoOptimizationCancellation(), successfulStorage(),
        transcoder, Runnable::run);
    Logger logger = (Logger) LoggerFactory.getLogger(AcademyVideoOptimizationWorker.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      worker.poll();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    String terminalLog = appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(message -> message.startsWith("academy_video_attempt"))
        .findFirst()
        .orElseThrow();
    assertThat(terminalLog)
        .contains("leaseTokenHash=7ac1b8d7010bb6cd")
        .containsPattern("leaseOwner=[0-9a-f\\-]{36}")
        .containsPattern("lastMediaProgressEpochMs=[1-9][0-9]*")
        .doesNotContain(claim().leaseToken());
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenStorageProviderResolutionFailure_whenPollRuns_thenFailureIsPropagated() {
    AcademyVideoOptimizationTransactions transactions =
        mock(AcademyVideoOptimizationTransactions.class);
    ObjectProvider<AcademyVideoWorkerStorage> storageProvider = mock(ObjectProvider.class);
    BeanCreationException failure = new BeanCreationException("worker storage is ambiguous");
    when(storageProvider.getIfAvailable()).thenThrow(failure);
    AcademyVideoOptimizationWorker worker = new AcademyVideoOptimizationWorker(
        transactions,
        new AcademyVideoOptimizationCancellation(),
        storageProvider,
        mock(VideoTranscoder.class),
        new AcademyVideoUploadProperties(),
        mock(Executor.class),
        mock(AcademyVideoMetrics.class));

    assertThatThrownBy(worker::poll).isSameAs(failure);
    verifyNoInteractions(transactions);
  }

  @Test
  void givenOccupiedLocalSlot_whenPollRunsAgain_thenDatabaseIsNotClaimedAgain() {
    AcademyVideoOptimizationTransactions transactions =
        mock(AcademyVideoOptimizationTransactions.class);
    when(transactions.claimNext(anyString())).thenReturn(
        new OptimizationClaim(1L, 2L, 1, "00000000-0000-0000-0000-000000000001", 0, "raw"));
    Executor capturingExecutor = mock(Executor.class);
    AcademyVideoWorkerStorage storage = mock(AcademyVideoWorkerStorage.class);
    var storageProvider = new StaticListableBeanFactory(
        java.util.Map.of("workerStorage", storage))
        .getBeanProvider(AcademyVideoWorkerStorage.class);
    AcademyVideoOptimizationWorker worker = new AcademyVideoOptimizationWorker(
        transactions,
        new AcademyVideoOptimizationCancellation(),
        storageProvider,
        mock(VideoTranscoder.class),
        new AcademyVideoUploadProperties(),
        capturingExecutor,
        mock(AcademyVideoMetrics.class));

    worker.poll();
    worker.poll();

    verify(transactions, times(1)).claimNext(anyString());
    verify(capturingExecutor, times(1)).execute(any(Runnable.class));
  }

  @Test
  void givenConversionThreadOccupied_whenHeartbeatRuns_thenItAdvancesIndependently() {
    AcademyVideoOptimizationTransactions transactions =
        mock(AcademyVideoOptimizationTransactions.class);
    when(transactions.claimNext(anyString())).thenReturn(claim());
    when(transactions.heartbeat(9L, "heartbeat-token", false)).thenReturn(true);
    AcademyVideoOptimizationCancellation cancellation =
        new AcademyVideoOptimizationCancellation();
    AcademyVideoOptimizationWorker worker = worker(
        transactions, cancellation, mock(AcademyVideoWorkerStorage.class),
        mock(VideoTranscoder.class), mock(Executor.class));

    worker.poll(); // conversion runnable remains captured/occupied
    try (var ignored = cancellation.register(9L, "heartbeat-token")) {
      worker.heartbeatActiveAttempts();
    }

    verify(transactions).heartbeat(9L, "heartbeat-token", false);
  }

  @Test
  void givenHeartbeatCasLoss_whenProcessObservesHandle_thenCancellationKillsProcessTree()
      throws Exception {
    AcademyVideoOptimizationTransactions transactions =
        mock(AcademyVideoOptimizationTransactions.class);
    when(transactions.heartbeat(9L, "lost-token", false)).thenReturn(false);
    AcademyVideoOptimizationCancellation cancellation =
        new AcademyVideoOptimizationCancellation();
    AcademyVideoOptimizationWorker worker = worker(
        transactions, cancellation, mock(AcademyVideoWorkerStorage.class),
        mock(VideoTranscoder.class), Runnable::run);

    try (var handle = cancellation.register(9L, "lost-token")) {
      worker.heartbeatActiveAttempts();
      assertThat(handle.cancelled()).isTrue();
      assertThatThrownBy(() -> new DefaultProcessRunner().runProgressAware(
          List.of("sh", "-c", "sleep 10 & wait"),
          Duration.ofMinutes(10), Duration.ZERO, handle::cancelled))
          .isInstanceOf(ProcessCancelledException.class);
    }
  }

  @Test
  void givenPublishAndCleanupDbFailures_whenAttemptAssetsExist_thenBothAreDeletedImmediately()
      throws Exception {
    AcademyVideoOptimizationTransactions transactions =
        mock(AcademyVideoOptimizationTransactions.class);
    when(transactions.claimNext(anyString())).thenReturn(claim());
    when(transactions.publish(any(), any(), any()))
        .thenThrow(new IllegalStateException("publication db failed"));
    doThrow(new IllegalStateException("cleanup db failed"))
        .when(transactions).enqueueAttemptCleanup(any(), anyString(), anyString(), anyString());
    AcademyVideoWorkerStorage storage = successfulStorage();
    doThrow(new AcademyVideoStorageException("OBJECT_MISSING"))
        .when(storage).deleteWorkerObject(normalizedKey());
    VideoTranscoder transcoder = successfulTranscoder();
    AcademyVideoOptimizationWorker worker =
        worker(transactions, new AcademyVideoOptimizationCancellation(), storage, transcoder,
            Runnable::run);

    worker.poll();

    verify(storage).deleteWorkerObject(normalizedKey());
    verify(storage).deleteWorkerObject(thumbnailKey());
    verify(transactions).retryableFailure(
        eq(1L), eq(claim().leaseToken()), anyString(), anyString());
  }

  @Test
  void givenUploadFailure_whenAttemptMayExist_thenCleanupIsQueuedAndBothKeysAreDeleted()
      throws Exception {
    AcademyVideoOptimizationTransactions transactions =
        mock(AcademyVideoOptimizationTransactions.class);
    when(transactions.claimNext(anyString())).thenReturn(claim());
    AcademyVideoWorkerStorage storage = successfulStorage();
    when(storage.upload(eq(normalizedKey()), any(), eq("video/mp4"), any()))
        .thenThrow(new IOException("upload response lost"));
    AcademyVideoOptimizationWorker worker = worker(
        transactions, new AcademyVideoOptimizationCancellation(), storage,
        successfulTranscoder(), Runnable::run);

    worker.poll();

    verify(transactions).enqueueAttemptCleanup(
        eq(claim()), eq(normalizedKey()), eq(thumbnailKey()), eq("ATTEMPT_NOT_PUBLISHED"));
    verify(storage).deleteWorkerObject(normalizedKey());
    verify(storage).deleteWorkerObject(thumbnailKey());
  }

  private AcademyVideoWorkerStorage successfulStorage() throws Exception {
    AcademyVideoWorkerStorage storage = mock(AcademyVideoWorkerStorage.class);
    when(storage.attemptKey(1L, 1, claim().leaseToken(), "normalized.mp4"))
        .thenReturn(normalizedKey());
    when(storage.attemptKey(1L, 1, claim().leaseToken(), "thumbnail.jpg"))
        .thenReturn(thumbnailKey());
    doAnswer(invocation -> {
      Files.writeString(invocation.getArgument(1), "raw");
      return null;
    }).when(storage).materialize(anyString(), any(Path.class), any(Duration.class));
    when(storage.upload(anyString(), any(Path.class), anyString(), any(Duration.class)))
        .thenReturn(new AcademyVideoWorkerStorage.StoredObject(10));
    when(storage.inspectWorkerObject(eq(normalizedKey()), any()))
        .thenReturn(new AcademyVideoWorkerStorage.StoredObject(10));
    when(storage.inspectWorkerObject(eq(thumbnailKey()), any()))
        .thenReturn(new AcademyVideoWorkerStorage.StoredObject(5));
    return storage;
  }

  private VideoTranscoder successfulTranscoder() throws Exception {
    VideoTranscoder transcoder = mock(VideoTranscoder.class);
    when(transcoder.probe(any(Path.class), any(Duration.class), any()))
        .thenReturn(new VideoProbe(720, 1280, "hevc", 2));
    Path mp4 = Files.writeString(temp.resolve("output.mp4"), "mp4");
    Path thumbnail = Files.writeString(temp.resolve("thumbnail.jpg"), "jpg");
    when(transcoder.transcodeAttempt(any(), any(), any(), any(), any(), any()))
        .thenReturn(new TranscodeResult(mp4, thumbnail, 720, 1280, 2, 3));
    return transcoder;
  }

  private AcademyVideoOptimizationWorker worker(
      AcademyVideoOptimizationTransactions transactions,
      AcademyVideoOptimizationCancellation cancellation,
      AcademyVideoWorkerStorage storage,
      VideoTranscoder transcoder,
      Executor executor) {
    var storageProvider = new StaticListableBeanFactory(
        java.util.Map.of("workerStorage", storage))
        .getBeanProvider(AcademyVideoWorkerStorage.class);
    AcademyVideoUploadProperties properties = new AcademyVideoUploadProperties();
    properties.setOptimizationTempDir(temp.resolve("work").toString());
    return new AcademyVideoOptimizationWorker(
        transactions, cancellation, storageProvider, transcoder, properties, executor,
        mock(AcademyVideoMetrics.class));
  }

  private static OptimizationClaim claim() {
    return new OptimizationClaim(
        1L, 2L, 1, "00000000-0000-0000-0000-000000000001", 0, "raw");
  }

  private static String normalizedKey() {
    return claim().attemptPrefix() + "/normalized.mp4";
  }

  private static String thumbnailKey() {
    return claim().attemptPrefix() + "/thumbnail.jpg";
  }
}
