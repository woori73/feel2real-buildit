package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.CompleteRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.CompletedPart;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.InitiateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoUploadSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoAssetCleanupRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoUploadSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoStorageException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoUploadStorage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AcademyVideoUploadSessionService — durable multipart finalization")
class AcademyVideoUploadSessionServiceTest {
  private AcademyService academyService;
  private AcademyRepository academyRepository;
  private AcademyVideoRepository videoRepository;
  private AcademyVideoUploadSessionRepository sessionRepository;
  private AcademyVideoOptimizationJobRepository jobRepository;
  private AcademyVideoAssetCleanupRepository cleanupRepository;
  private AcademyVideoUploadStorage storage;
  private AcademyVideoRawValidator validator;
  private AcademyVideoFinalizationCancellation cancellation;
  private TransactionTemplate transactions;
  private EntityManager entityManager;
  private AcademyVideoUploadSessionService service;
  private Academy academy;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private LocalDateTime now;

  @BeforeEach
  void setUp() {
    academyService = mock(AcademyService.class);
    academyRepository = mock(AcademyRepository.class);
    videoRepository = mock(AcademyVideoRepository.class);
    sessionRepository = mock(AcademyVideoUploadSessionRepository.class);
    jobRepository = mock(AcademyVideoOptimizationJobRepository.class);
    cleanupRepository = mock(AcademyVideoAssetCleanupRepository.class);
    storage = mock(AcademyVideoUploadStorage.class);
    validator = mock(AcademyVideoRawValidator.class);
    cancellation = new AcademyVideoFinalizationCancellation();
    transactions = mock(TransactionTemplate.class);
    entityManager = mock(EntityManager.class);
    TransactionStatus transactionStatus = mock(TransactionStatus.class);
    when(transactions.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(transactionStatus);
    });
    org.mockito.Mockito.doAnswer(invocation -> {
      Consumer<TransactionStatus> callback = invocation.getArgument(0);
      callback.accept(transactionStatus);
      return null;
    }).when(transactions).executeWithoutResult(any());

    User tutor = User.builder()
        .loginId("tutor").password("pw").name("강사").phone("010").build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    academy = Academy.builder()
        .tutor(tutor).slug("academy").name("academy")
        .logoStorageKey("logo").intro("intro").build();
    ReflectionTestUtils.setField(academy, "academyId", 100L);
    principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(academyService.requireMyAcademy(principal)).thenReturn(academy);
    when(academyRepository.findForUpdateByAcademyId(100L)).thenReturn(Optional.of(academy));
    when(videoRepository.countByAcademy_AcademyIdAndLifecycleStatusNot(
        100L, VideoLifecycleStatus.DELETING)).thenReturn(0L);
    when(sessionRepository
        .countByAcademy_AcademyIdAndStateInAndDeadlineAtGreaterThanEqual(
            eq(100L), any(), any())).thenReturn(0L);
    when(cleanupRepository.findByDedupeKey(anyString())).thenReturn(Optional.empty());
    when(cleanupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(videoRepository.save(any())).thenAnswer(invocation -> {
      var video = invocation.getArgument(0,
          f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo.class);
      ReflectionTestUtils.setField(video, "videoId", 7L);
      return video;
    });
    when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    now = LocalDateTime.of(2026, 7, 30, 12, 0);
    when(sessionRepository.databaseNow()).thenReturn(now);

    service = new AcademyVideoUploadSessionService(
        academyService, academyRepository, videoRepository, sessionRepository, jobRepository,
        cleanupRepository, storage, validator, cancellation, transactions, entityManager,
        new AcademyVideoUploadProperties(),
        tools.jackson.databind.json.JsonMapper.builder().build(),
        mock(AcademyVideoMetrics.class));
  }

  @Test
  @DisplayName("given declared size exactly 209715200 when initiate then ten capabilities are reserved")
  void initiate_accepts_exact_size_boundary() {
    // given
    String sessionId = "11111111-1111-1111-1111-111111111111";
    when(storage.initiate(anyString(), eq(209_715_200L), eq(10)))
        .thenAnswer(invocation -> new AcademyVideoUploadStorage.UploadSession(
            "staging/" + invocation.getArgument(0) + "/raw", "upload-id",
            java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "https://opaque.invalid/" + i).toList()));
    when(sessionRepository.findById(anyString())).thenAnswer(invocation -> Optional.of(
        initiated(invocation.getArgument(0), 209_715_200L)));

    // when
    var response = service.initiate(principal,
        new InitiateRequest("clip.mov", 209_715_200L));

    // then
    assertThat(response.partSizeBytes()).isEqualTo(20L * 1024 * 1024);
    assertThat(response.parts()).hasSize(10);
    verify(sessionRepository).insertInitiatedUsingDatabaseClock(
        anyString(), eq(100L), eq("clip.mov"), eq(209_715_200L),
        anyString(), eq("upload-id"), eq(20L * 1024 * 1024), eq(10), eq(180_000_000L));
  }

  @Test
  @DisplayName("given owned session when observe after refresh then safe timing state is returned")
  void observe_returns_safe_owned_state() {
    AcademyVideoUploadSession session = initiated("session", 1L);
    when(sessionRepository.findBySessionIdAndAcademy_AcademyId("session", 100L))
        .thenReturn(Optional.of(session));

    var response = service.observe(principal, "session");

    assertThat(response.state()).isEqualTo(VideoUploadSessionState.INITIATED);
    assertThat(response.serverNow()).isEqualTo(now);
    assertThat(response.deadlineAt()).isEqualTo(now.plusMinutes(3));
  }

  @Test
  @DisplayName("given declared size 209715201 when initiate then reject before storage")
  void initiate_rejects_over_size_boundary_before_storage() {
    // given / when / then
    assertThatThrownBy(() -> service.initiate(
        principal, new InitiateRequest("clip.mov", 209_715_201L)))
        .isInstanceOf(AcademyVideoUploadApiException.class)
        .extracting("code").isEqualTo("RAW_SIZE_LIMIT");
    verify(storage, never()).initiate(anyString(), anyLong(), any(Integer.class));
  }

  @Test
  @DisplayName("given thirty committed videos when initiate then reject before multipart creation")
  void initiate_rejects_quota_before_storage() {
    // given
    when(videoRepository.countByAcademy_AcademyIdAndLifecycleStatusNot(
        100L, VideoLifecycleStatus.DELETING)).thenReturn(30L);

    // when / then
    assertThatThrownBy(() -> service.initiate(
        principal, new InitiateRequest("clip.mov", 1)))
        .isInstanceOf(AcademyVideoUploadApiException.class)
        .extracting("code").isEqualTo("ACADEMY_VIDEO_QUOTA");
    verify(storage, never()).initiate(anyString(), anyLong(), any(Integer.class));
  }

  @Test
  @DisplayName("given valid object when complete then READY RAW is accepted without transcode job")
  void complete_accepts_valid_raw_without_transcode_job() {
    // given
    AcademyVideoUploadSession session = initiated(
        "11111111-1111-1111-1111-111111111111", 3);
    ReflectionTestUtils.setField(session, "deadlineAt", now);
    when(sessionRepository.databaseNow()).thenReturn(now.minusSeconds(1), now);
    when(sessionRepository.findWithLockBySessionIdAndAcademyId(
        session.getSessionId(), 100L)).thenReturn(Optional.of(session));
    when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
    when(storage.inspect(anyString(), anyString(), any(Duration.class)))
        .thenReturn(new AcademyVideoUploadStorage.StorageState(true, false));
    when(storage.complete(anyString(), anyString(), any(), any(Duration.class)))
        .thenReturn(new AcademyVideoUploadStorage.CompletedObject("etag", 3));
    when(storage.head(anyString(), any(Duration.class)))
        .thenReturn(new AcademyVideoUploadStorage.ObjectMetadata("etag", 3));
    when(validator.validate(anyString(), anyString(), anyString(), eq(3L), any(), any()))
        .thenReturn(new AcademyVideoRawValidator.ValidatedRaw(
            "video/mp4", 3, 120_000, 1280, 720));
    CompleteRequest request = new CompleteRequest(List.of(new CompletedPart(1, "etag", null)));

    // when
    var result = service.complete(principal, session.getSessionId(), request);

    // then
    assertThat(result.status()).isEqualTo(HttpStatus.OK);
    assertThat(result.response().state()).isEqualTo(VideoUploadSessionState.ACCEPTED);
    assertThat(result.response().acceptedVideoId()).isEqualTo(7L);
    verify(videoRepository).save(any());
    verify(jobRepository, never()).save(any());
  }

  @Test
  @DisplayName("given webm filename when initiate then reject before multipart creation")
  void initiate_rejects_webm_before_storage() {
    assertThatThrownBy(() -> service.initiate(
        principal, new InitiateRequest("clip.webm", 1024L)))
        .isInstanceOf(AcademyVideoUploadApiException.class)
        .extracting("code").isEqualTo("UNSUPPORTED_VIDEO_FORMAT");
    verify(storage, never()).initiate(anyString(), anyLong(), any(Integer.class));
  }

  @Test
  @DisplayName("given DB time deadline plus one microsecond when complete then expire before storage")
  void completion_after_deadline_expires_and_enqueues_cleanup() {
    // given
    AcademyVideoUploadSession session = initiated(
        "11111111-1111-1111-1111-111111111111", 3);
    ReflectionTestUtils.setField(session, "deadlineAt", now.minusNanos(1_000));
    when(sessionRepository.findWithLockBySessionIdAndAcademyId(
        session.getSessionId(), 100L)).thenReturn(Optional.of(session));

    // when
    var result = service.complete(principal, session.getSessionId(),
        new CompleteRequest(List.of(new CompletedPart(1, "etag", null))));

    // then
    assertThat(result.status()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
    assertThat(result.response().state()).isEqualTo(VideoUploadSessionState.EXPIRED);
    verify(storage, never()).inspect(anyString(), anyString(), any(Duration.class));
    verify(cleanupRepository, atLeastOnce()).insertPendingIgnoreDuplicate(
        anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), anyString(), eq(now));
  }

  @Test
  @DisplayName("given transient Phase B failure when client replays then replay observes 202 and does no work")
  void transient_retry_is_recovery_worker_exclusive() {
    // given
    AcademyVideoUploadSession session = initiated(
        "11111111-1111-1111-1111-111111111111", 3);
    when(sessionRepository.findWithLockBySessionIdAndAcademyId(
        session.getSessionId(), 100L)).thenReturn(Optional.of(session));
    when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
    when(storage.inspect(anyString(), anyString(), any(Duration.class)))
        .thenThrow(new AcademyVideoStorageException("S3_INSPECT_FAILED"));
    CompleteRequest request = new CompleteRequest(List.of(new CompletedPart(1, "etag", null)));

    // when
    var first = service.complete(principal, session.getSessionId(), request);
    var replay = service.complete(principal, session.getSessionId(), request);

    // then
    assertThat(first.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(replay.status()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(replay.response().retryAfterSeconds()).isEqualTo(5);
    verify(storage).inspect(anyString(), anyString(), any(Duration.class));
    verify(validator, never()).validate(anyString(), anyString(), anyString(), anyLong(), any(), any());
  }

  @Test
  @DisplayName("given malformed or audio-only raw when validate then terminal reject and cleanup outbox")
  void permanent_validation_failure_rejects_and_enqueues_cleanup() {
    // given
    AcademyVideoUploadSession session = initiated(
        "11111111-1111-1111-1111-111111111111", 3);
    when(sessionRepository.findWithLockBySessionIdAndAcademyId(
        session.getSessionId(), 100L)).thenReturn(Optional.of(session));
    when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
    when(storage.inspect(anyString(), anyString(), any(Duration.class)))
        .thenReturn(new AcademyVideoUploadStorage.StorageState(false, true));
    when(storage.head(anyString(), any(Duration.class)))
        .thenReturn(new AcademyVideoUploadStorage.ObjectMetadata("etag", 3));
    when(validator.validate(anyString(), anyString(), anyString(), eq(3L), any(), any()))
        .thenThrow(new AcademyVideoRawValidationException("VIDEO_STREAM_REQUIRED", false));

    // when
    var result = service.complete(principal, session.getSessionId(),
        new CompleteRequest(List.of(new CompletedPart(1, "etag", null))));

    // then
    assertThat(result.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(result.response().state()).isEqualTo(VideoUploadSessionState.REJECTED);
    assertThat(result.response().errorCode()).isEqualTo("VIDEO_STREAM_REQUIRED");
    verify(cleanupRepository, atLeastOnce()).insertPendingIgnoreDuplicate(
        anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), anyString(), eq(now));
    verify(videoRepository, never()).save(any());
    verify(jobRepository, never()).save(any());
  }

  @Test
  @DisplayName("given network-lost abort and expired INITIATED when reaper runs then EXPIRED and cleanup persist")
  void initiated_expiry_reaper_is_terminal_and_durable() {
    // given
    AcademyVideoUploadSession session = initiated(
        "11111111-1111-1111-1111-111111111111", 3);
    ReflectionTestUtils.setField(session, "deadlineAt", now.minusNanos(1_000));
    when(sessionRepository.findTop100ByStateInAndDeadlineAtLessThanOrderByDeadlineAt(
        any(), eq(now))).thenReturn(List.of(session));
    when(sessionRepository.findWithLockBySessionIdAndAcademyId(
        session.getSessionId(), 100L)).thenReturn(Optional.of(session));

    // when
    service.expireAbandonedSessions();

    // then
    assertThat(session.getState()).isEqualTo(VideoUploadSessionState.EXPIRED);
    assertThat(session.getLastErrorCode()).isEqualTo("DEADLINE_EXCEEDED");
    verify(cleanupRepository, org.mockito.Mockito.times(2)).insertPendingIgnoreDuplicate(
        anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), anyString(), eq(now));
    verify(storage).abort(session.getStagingKey(), session.getMultipartUploadId());
  }

  @Test
  @DisplayName("given ACCEPTED replay after deadline when complete then same video wins without storage")
  void accepted_replay_wins_after_deadline() {
    // given
    AcademyVideoUploadSession session = initiated(
        "11111111-1111-1111-1111-111111111111", 3);
    var video = f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity
        .AcademyVideo.readyRaw(academy, "clip.webm", session.getStagingKey(),
            "video/webm", 3, 1_000, 10, 10);
    ReflectionTestUtils.setField(video, "videoId", 7L);
    session.beginFinalization(
        "33333333-3333-3333-3333-333333333333", "[]", "hash", now.minusMinutes(4));
    session.accept(video, now.minusMinutes(3));
    ReflectionTestUtils.setField(session, "deadlineAt", now.minusMinutes(1));
    when(sessionRepository.findWithLockBySessionIdAndAcademyId(
        session.getSessionId(), 100L)).thenReturn(Optional.of(session));

    // when
    var result = service.complete(principal, session.getSessionId(),
        new CompleteRequest(List.of(new CompletedPart(1, "different", null))));

    // then
    assertThat(result.status()).isEqualTo(HttpStatus.OK);
    assertThat(result.response().acceptedVideoId()).isEqualTo(7L);
    verify(storage, never()).inspect(anyString(), anyString(), any(Duration.class));
  }

  private AcademyVideoUploadSession initiated(String sessionId, long size) {
    return AcademyVideoUploadSession.builder()
        .sessionId(sessionId)
        .academy(academy)
        .requestedFilename("clip.webm")
        .declaredSizeBytes(size)
        .state(VideoUploadSessionState.INITIATED)
        .stagingKey("staging/" + sessionId + "/raw")
        .multipartUploadId("22222222-2222-2222-2222-222222222222")
        .partSizeBytes(20L * 1024 * 1024)
        .expectedPartCount((int) ((size + 20L * 1024 * 1024 - 1) / (20L * 1024 * 1024)))
        .deadlineAt(now.plusMinutes(3))
        .build();
  }
}
