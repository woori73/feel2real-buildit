package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.CompleteRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.CompletedPart;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.InitiateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoOptimizationJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoUploadSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoRawValidator;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoUploadSessionService;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoStorageException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoUploadStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "app.video.upload-recovery-poll-ms=600000",
    "app.video.optimization-enabled=false"
})
@DisplayName("Academy video Ready transaction — real MySQL atomicity")
class AcademyVideoUploadFinalizeTransactionTest {
  private static final String FILE_PREFIX = "g002-atomic-";

  @Autowired AcademyVideoUploadSessionService service;
  @Autowired AcademyRepository academyRepository;
  @Autowired JdbcTemplate jdbc;

  @MockitoBean AcademyService academyService;
  @MockitoBean AcademyVideoUploadStorage storage;
  @MockitoBean AcademyVideoRawValidator validator;
  @MockitoSpyBean AcademyVideoUploadSessionRepository sessionRepository;
  @MockitoSpyBean AcademyVideoRepository videoRepository;
  @MockitoSpyBean AcademyVideoOptimizationJobRepository jobRepository;

  private Academy academy;
  private JwtTokenProvider.AuthenticatedPrincipal principal;

  @BeforeEach
  void setUp() {
    academy = academyRepository.findAll().stream().findFirst().orElseThrow();
    var tutor = jdbc.queryForMap("""
        SELECT u.user_id, u.login_id
        FROM academies a JOIN users u ON u.user_id = a.tutor_user_id
        WHERE a.academy_id = ?
        """, academy.getAcademyId());
    principal = new JwtTokenProvider.AuthenticatedPrincipal(
        ((Number) tutor.get("user_id")).longValue(), (String) tutor.get("login_id"), "TUTOR");
    when(academyService.requireMyAcademy(principal)).thenReturn(academy);
    when(storage.initiate(anyString(), anyLong(), any(Integer.class)))
        .thenAnswer(invocation -> {
          String sessionId = invocation.getArgument(0);
          return new AcademyVideoUploadStorage.UploadSession(
              "staging/" + sessionId + "/raw", "upload-" + sessionId,
              List.of("https://opaque.invalid/part/1"));
        });
    when(storage.inspect(anyString(), anyString(), any(Duration.class)))
        .thenReturn(new AcademyVideoUploadStorage.StorageState(false, true));
    when(storage.head(anyString(), any(Duration.class)))
        .thenReturn(new AcademyVideoUploadStorage.ObjectMetadata("etag", 3));
    when(validator.validate(anyString(), anyString(), anyString(), anyLong(), any(), any()))
        .thenReturn(new AcademyVideoRawValidator.ValidatedRaw(
            "video/mp4", 3, 120_000, 1280, 720));
  }

  @AfterEach
  void cleanUp() {
    reset(jobRepository, videoRepository, sessionRepository);
    jdbc.update("""
        DELETE c FROM academy_video_asset_cleanups c
        JOIN academy_video_upload_sessions s ON s.session_id = c.session_id
        WHERE s.requested_filename LIKE ?
        """, FILE_PREFIX + "%");
    jdbc.update("""
        DELETE FROM academy_video_upload_sessions
        WHERE requested_filename LIKE ?
        """, FILE_PREFIX + "%");
    jdbc.update("""
        DELETE j FROM academy_video_optimization_jobs j
        JOIN academy_videos v ON v.video_id = j.video_id
        WHERE v.original_filename LIKE ?
        """, FILE_PREFIX + "%");
    jdbc.update("DELETE FROM academy_videos WHERE original_filename LIKE ?", FILE_PREFIX + "%");
  }

  @Test
  @DisplayName("given valid raw when Phase C commits then ACCEPTED and READY RAW appear without a job")
  void phase_c_commits_raw_without_transcode_job() {
    // given
    String sessionId = initiate("success");

    // when
    var result = service.complete(principal, sessionId,
        new CompleteRequest(List.of(new CompletedPart(1, "etag", null))));

    // then
    assertThat(result.status()).isEqualTo(HttpStatus.OK);
    var session = sessionRepository.findById(sessionId).orElseThrow();
    assertThat(session.getState()).isEqualTo(VideoUploadSessionState.ACCEPTED);
    assertThat(session.getAcceptedVideo()).isNotNull();
    assertThat(videoRepository.findById(session.getAcceptedVideo().getVideoId())).isPresent();
    assertThat(jobRepository.findByVideo_VideoId(session.getAcceptedVideo().getVideoId()))
        .isEmpty();
  }

  @Test
  @DisplayName("given job repository is unavailable when Phase C commits then raw acceptance is independent")
  void job_repository_is_not_used_for_raw_acceptance() {
    // given
    String sessionId = initiate("rollback");
    doThrow(new IllegalStateException("injected job failure"))
        .when(jobRepository).save(any(AcademyVideoOptimizationJob.class));

    // when
    var result = service.complete(principal, sessionId,
        new CompleteRequest(List.of(new CompletedPart(1, "etag", null))));

    // then
    assertThat(result.status()).isEqualTo(HttpStatus.OK);
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.ACCEPTED);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_videos WHERE original_filename = ?",
        Long.class, FILE_PREFIX + "rollback.mp4")).isOne();
    verify(jobRepository, never()).save(any(AcademyVideoOptimizationJob.class));
  }

  @Test
  @DisplayName("given video insert fails when Phase C rolls back then job and ACCEPTED are absent")
  void video_failure_rolls_back_phase_c() {
    // given
    String sessionId = initiate("video-rollback");
    doThrow(new IllegalStateException("injected video failure"))
        .when(videoRepository).save(any());

    // when
    var result = service.complete(principal, sessionId, completeRequest());

    // then
    assertThat(result.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.COMPLETING);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_videos WHERE original_filename = ?",
        Long.class, FILE_PREFIX + "video-rollback.mp4")).isZero();
  }

  @Test
  @DisplayName("given session ACCEPTED update fails when Phase C commits then video and job roll back")
  void session_update_failure_rolls_back_video_and_job() {
    // given
    String sessionId = initiate("session-rollback");
    doThrow(new IllegalStateException("injected session flush failure"))
        .when(sessionRepository).flush();

    // when
    var result = service.complete(principal, sessionId, completeRequest());

    // then
    assertThat(result.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.COMPLETING);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_videos WHERE original_filename = ?",
        Long.class, FILE_PREFIX + "session-rollback.mp4")).isZero();
    assertThat(jdbc.queryForObject("""
        SELECT COUNT(*) FROM academy_video_optimization_jobs j
        JOIN academy_videos v ON v.video_id = j.video_id
        WHERE v.original_filename = ?
        """, Long.class, FILE_PREFIX + "session-rollback.mp4")).isZero();
  }

  @Test
  @DisplayName("given one quota slot when two initiates race then exactly one reservation commits")
  void concurrent_initiate_enforces_quota_boundary() throws Exception {
    // given
    long videos = videoRepository.countByAcademy_AcademyIdAndLifecycleStatusNot(
        academy.getAcademyId(),
        f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity
            .VideoLifecycleStatus.DELETING);
    long existingReservations = sessionRepository
        .countByAcademy_AcademyIdAndStateInAndDeadlineAtGreaterThanEqual(
            academy.getAcademyId(),
            List.of(VideoUploadSessionState.INITIATED, VideoUploadSessionState.COMPLETING),
            sessionRepository.databaseNow());
    int reservationsToCreate = Math.toIntExact(9 - videos - existingReservations);
    assertThat(reservationsToCreate).isGreaterThanOrEqualTo(0);
    for (int i = 0; i < reservationsToCreate; i++) {
      initiate("quota-fill-" + i);
    }
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());

    // when
    try (var executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        int index = i;
        executor.submit(() -> {
          try {
            start.await();
            initiate("quota-race-" + index);
            successes.incrementAndGet();
          } catch (Throwable failure) {
            failures.add(failure);
          }
          return null;
        });
      }
      start.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    // then
    assertThat(successes.get()).isOne();
    assertThat(failures).hasSize(1);
    assertThat(failures.getFirst())
        .isInstanceOf(f2r.tutor_solution.private_tutor_solution_back.domain.academy.service
            .AcademyVideoUploadApiException.class);
    long live = sessionRepository
        .countByAcademy_AcademyIdAndStateInAndDeadlineAtGreaterThanEqual(
            academy.getAcademyId(),
            List.of(VideoUploadSessionState.INITIATED, VideoUploadSessionState.COMPLETING),
            sessionRepository.databaseNow());
    assertThat(videos + live).isEqualTo(10);
  }

  @Test
  @DisplayName("given concurrent complete calls when Phase A is held then only one runs external work")
  void concurrent_complete_runs_single_finalizer() throws Exception {
    // given
    String sessionId = initiate("complete-race");
    CountDownLatch enteredStorage = new CountDownLatch(1);
    CountDownLatch releaseStorage = new CountDownLatch(1);
    doAnswer(invocation -> {
      enteredStorage.countDown();
      assertThat(releaseStorage.await(5, TimeUnit.SECONDS)).isTrue();
      return new AcademyVideoUploadStorage.StorageState(false, true);
    }).when(storage).inspect(anyString(), anyString(), any(Duration.class));

    // when
    var request = completeRequest();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.complete(principal, sessionId, request));
      assertThat(enteredStorage.await(5, TimeUnit.SECONDS)).isTrue();
      var replay = executor.submit(() -> service.complete(principal, sessionId, request));
      assertThat(replay.get(5, TimeUnit.SECONDS).status()).isEqualTo(HttpStatus.ACCEPTED);
      releaseStorage.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(HttpStatus.OK);
    }

    // then
    verify(storage, times(1)).inspect(anyString(), anyString(), any(Duration.class));
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_videos WHERE original_filename = ?",
        Long.class, FILE_PREFIX + "complete-race.mp4")).isOne();
  }

  @Test
  @DisplayName("given abort commits first when late finalizer resumes then no video is accepted")
  void abort_first_fences_late_phase_c() throws Exception {
    // given
    String sessionId = initiate("abort-wins");
    CountDownLatch enteredStorage = new CountDownLatch(1);
    CountDownLatch releaseStorage = new CountDownLatch(1);
    doAnswer(invocation -> {
      enteredStorage.countDown();
      assertThat(releaseStorage.await(5, TimeUnit.SECONDS)).isTrue();
      return new AcademyVideoUploadStorage.StorageState(false, true);
    }).when(storage).inspect(anyString(), anyString(), any(Duration.class));

    // when
    try (var executor = Executors.newSingleThreadExecutor()) {
      var completing = executor.submit(
          () -> service.complete(principal, sessionId, completeRequest()));
      assertThat(enteredStorage.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(service.abort(principal, sessionId).status()).isEqualTo(HttpStatus.NO_CONTENT);
      releaseStorage.countDown();
      assertThat(completing.get(5, TimeUnit.SECONDS).status()).isEqualTo(HttpStatus.CONFLICT);
    }

    // then
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.ABORTED);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_videos WHERE original_filename = ?",
        Long.class, FILE_PREFIX + "abort-wins.mp4")).isZero();
  }

  @Test
  @DisplayName("given Phase C commits first when abort follows then accepted outcome is preserved")
  void phase_c_first_wins_over_abort() {
    // given
    String sessionId = initiate("accept-wins");
    assertThat(service.complete(principal, sessionId, completeRequest()).status())
        .isEqualTo(HttpStatus.OK);

    // when
    var abort = service.abort(principal, sessionId);

    // then
    assertThat(abort.status()).isEqualTo(HttpStatus.OK);
    assertThat(abort.response().state()).isEqualTo(VideoUploadSessionState.ACCEPTED);
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.ACCEPTED);
  }

  @Test
  @DisplayName("given complete side effect then storage exception when recovery runs object head resumes acceptance")
  void crash_after_storage_complete_recovers_from_durable_object() {
    // given
    String sessionId = initiate("storage-crash");
    AtomicInteger inspections = new AtomicInteger();
    when(storage.inspect(anyString(), anyString(), any(Duration.class)))
        .thenAnswer(invocation -> inspections.getAndIncrement() == 0
            ? new AcademyVideoUploadStorage.StorageState(true, false)
            : new AcademyVideoUploadStorage.StorageState(false, true));
    when(storage.complete(anyString(), anyString(), any(), any(Duration.class)))
        .thenThrow(new AcademyVideoStorageException("injected response loss"));

    // when
    assertThat(service.complete(principal, sessionId, completeRequest()).status())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    makeRecoveryDue(sessionId);
    service.recoverDueFinalizations();

    // then
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.ACCEPTED);
    assertThat(inspections.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("given validation process loss when recovery retries then persisted manifest reaches ACCEPTED")
  void crash_during_validation_recovers() {
    // given
    String sessionId = initiate("validation-crash");
    when(validator.validate(anyString(), anyString(), anyString(), anyLong(), any(), any()))
        .thenThrow(new IllegalStateException("injected process loss"))
        .thenReturn(new AcademyVideoRawValidator.ValidatedRaw(
            "video/mp4", 3, 120_000, 1280, 720));

    // when
    assertThat(service.complete(principal, sessionId, completeRequest()).status())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    makeRecoveryDue(sessionId);
    service.recoverDueFinalizations();

    // then
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.ACCEPTED);
    verify(validator, times(2))
        .validate(anyString(), anyString(), anyString(), anyLong(), any(), any());
  }

  @Test
  @DisplayName("given response is lost after acceptance when complete replays then no duplicate video is created")
  void accepted_response_replay_is_idempotent() {
    // given
    String sessionId = initiate("response-loss");
    assertThat(service.complete(principal, sessionId, completeRequest()).status())
        .isEqualTo(HttpStatus.OK);

    // when
    var replay = service.complete(principal, sessionId, completeRequest());

    // then
    assertThat(replay.status()).isEqualTo(HttpStatus.OK);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_videos WHERE original_filename = ?",
        Long.class, FILE_PREFIX + "response-loss.mp4")).isOne();
    verify(storage, times(1)).inspect(anyString(), anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("given an expired recovery lease when recovery scans then it is reclaimed and finalized")
  void expired_recovery_lease_is_reclaimed() {
    // given
    String sessionId = initiate("lease-reclaim");
    when(validator.validate(anyString(), anyString(), anyString(), anyLong(), any(), any()))
        .thenThrow(new IllegalStateException("first worker crashed"))
        .thenReturn(new AcademyVideoRawValidator.ValidatedRaw(
            "video/mp4", 3, 120_000, 1280, 720));
    assertThat(service.complete(principal, sessionId, completeRequest()).status())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    jdbc.update("""
        UPDATE academy_video_upload_sessions
        SET next_finalize_attempt_at = timestampadd(second, -1, current_timestamp(6)),
            finalize_recovery_owner = 'dead-worker',
            finalize_recovery_lease_expires_at = timestampadd(second, -1, current_timestamp(6))
        WHERE session_id = ?
        """, sessionId);

    // when
    service.recoverDueFinalizations();

    // then
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.ACCEPTED);
  }

  @Test
  @DisplayName("given one due session when two recovery scans race then only one worker performs external work")
  void concurrent_recovery_scans_claim_once() throws Exception {
    // given
    String sessionId = initiate("recovery-race");
    when(validator.validate(anyString(), anyString(), anyString(), anyLong(), any(), any()))
        .thenThrow(new IllegalStateException("make recovery due"))
        .thenReturn(new AcademyVideoRawValidator.ValidatedRaw(
            "video/mp4", 3, 120_000, 1280, 720));
    assertThat(service.complete(principal, sessionId, completeRequest()).status())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    makeRecoveryDue(sessionId);
    clearInvocations(storage);
    CountDownLatch enteredStorage = new CountDownLatch(1);
    CountDownLatch releaseStorage = new CountDownLatch(1);
    doAnswer(invocation -> {
      enteredStorage.countDown();
      assertThat(releaseStorage.await(5, TimeUnit.SECONDS)).isTrue();
      return new AcademyVideoUploadStorage.StorageState(false, true);
    }).when(storage).inspect(anyString(), anyString(), any(Duration.class));

    // when
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(service::recoverDueFinalizations);
      assertThat(enteredStorage.await(5, TimeUnit.SECONDS)).isTrue();
      var competing = executor.submit(service::recoverDueFinalizations);
      competing.get(5, TimeUnit.SECONDS);
      releaseStorage.countDown();
      first.get(5, TimeUnit.SECONDS);
    }

    // then
    verify(storage, times(1)).inspect(anyString(), anyString(), any(Duration.class));
    assertThat(sessionRepository.findById(sessionId).orElseThrow().getState())
        .isEqualTo(VideoUploadSessionState.ACCEPTED);
  }

  private String initiate(String suffix) {
    return service.initiate(principal,
        new InitiateRequest(FILE_PREFIX + suffix + ".mp4", 3)).sessionId();
  }

  private static CompleteRequest completeRequest() {
    return new CompleteRequest(List.of(new CompletedPart(1, "etag", null)));
  }

  private void makeRecoveryDue(String sessionId) {
    jdbc.update("""
        UPDATE academy_video_upload_sessions
        SET next_finalize_attempt_at = timestampadd(second, -1, current_timestamp(6))
        WHERE session_id = ?
        """, sessionId);
  }
}
