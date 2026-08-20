package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import tools.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.CompleteRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.CompletedPart;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.InitiateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.InitiateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.PartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.SessionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.SessionObservation;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoAssetCleanup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoOptimizationJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoUploadSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetCleanupState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoAssetCleanupRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoUploadSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoStorageException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoUploadStorage;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class AcademyVideoUploadSessionService {
  public static final long MAX_RAW_BYTES = AcademyVideoUploadProperties.REQUIRED_MAX_RAW_BYTES;
  public static final long PART_SIZE_BYTES =
      AcademyVideoUploadProperties.REQUIRED_PART_SIZE_BYTES;
  public static final int MAX_VIDEO_COUNT = AcademyVideoUploadProperties.REQUIRED_MAX_COUNT;
  private static final List<VideoUploadSessionState> LIVE_RESERVATIONS =
      List.of(VideoUploadSessionState.INITIATED, VideoUploadSessionState.COMPLETING);
  private static final java.util.Set<String> SUPPORTED_UPLOAD_EXTENSIONS =
      java.util.Set.of("mp4", "mov");

  private final AcademyService academyService;
  private final AcademyRepository academyRepository;
  private final AcademyVideoRepository videoRepository;
  private final AcademyVideoUploadSessionRepository sessionRepository;
  private final AcademyVideoOptimizationJobRepository jobRepository;
  private final AcademyVideoAssetCleanupRepository cleanupRepository;
  private final AcademyVideoUploadStorage storage;
  private final AcademyVideoRawValidator rawValidator;
  private final AcademyVideoFinalizationCancellation cancellation;
  private final TransactionTemplate transactions;
  private final EntityManager entityManager;
  private final AcademyVideoUploadProperties properties;
  private final ObjectMapper objectMapper;
  private final AcademyVideoMetrics metrics;
  private final String recoveryOwner = UUID.randomUUID().toString();

  public InitiateResponse initiate(
      JwtTokenProvider.AuthenticatedPrincipal principal, InitiateRequest request) {
    Academy owned = academyService.requireMyAcademy(principal);
    String filename = safeFilename(request.filename());
    if (!SUPPORTED_UPLOAD_EXTENSIONS.contains(extension(filename))) {
      throw api(HttpStatus.BAD_REQUEST, "UNSUPPORTED_VIDEO_FORMAT",
          "휴대폰에서 촬영한 MP4 또는 MOV 영상만 업로드할 수 있습니다.");
    }
    if (request.declaredSizeBytes() < 1
        || request.declaredSizeBytes() > properties.getMaxRawBytes()) {
      throw api(HttpStatus.BAD_REQUEST, "RAW_SIZE_LIMIT", "영상은 200MiB 이하여야 합니다.");
    }
    String sessionId = UUID.randomUUID().toString();
    int partCount = Math.toIntExact(
        (request.declaredSizeBytes() + properties.getUploadPartSizeBytes() - 1)
            / properties.getUploadPartSizeBytes());
    transactions.executeWithoutResult(status -> assertQuotaAvailable(owned.getAcademyId()));
    AcademyVideoUploadStorage.UploadSession upload =
        storage.initiate(sessionId, request.declaredSizeBytes(), partCount);
    try {
      AcademyVideoUploadSession session = transactions.execute(status -> {
        assertQuotaAvailable(owned.getAcademyId());
        sessionRepository.insertInitiatedUsingDatabaseClock(
            sessionId, owned.getAcademyId(), filename, request.declaredSizeBytes(),
            upload.stagingKey(), upload.uploadId(), properties.getUploadPartSizeBytes(), partCount,
            properties.getUploadDeadlineMs() * 1_000);
        entityManager.flush();
        entityManager.clear();
        return sessionRepository.findById(sessionId).orElseThrow();
      });
      if (session == null) throw new IllegalStateException("session transaction returned null");
      LocalDateTime serverNow = sessionRepository.databaseNow();
      List<PartCapability> capabilities = java.util.stream.IntStream.range(0, partCount)
          .mapToObj(i -> new PartCapability(i + 1, upload.opaquePartUrls().get(i)))
          .toList();
      return new InitiateResponse(
          sessionId, serverNow, session.getDeadlineAt(), PART_SIZE_BYTES, capabilities);
    } catch (RuntimeException e) {
      storage.abort(upload.stagingKey(), upload.uploadId());
      throw e;
    }
  }

  public String uploadLocalPart(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String sessionId,
      int partNumber,
      String capability,
      InputStream body,
      long contentLength) throws java.io.IOException {
    Academy academy = academyService.requireMyAcademy(principal);
    AcademyVideoUploadSession session =
        sessionRepository.findBySessionIdAndAcademy_AcademyId(sessionId, academy.getAcademyId())
            .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "UPLOAD_SESSION_NOT_FOUND",
                "업로드 세션을 찾을 수 없습니다."));
    LocalDateTime now = sessionRepository.databaseNow();
    if (session.getState() != VideoUploadSessionState.INITIATED
        || now.isAfter(session.getDeadlineAt())
        || !constantTimeEquals(capability, session.getMultipartUploadId())
        || partNumber < 1 || partNumber > session.getExpectedPartCount()) {
      throw api(HttpStatus.CONFLICT, "UPLOAD_PART_NOT_ALLOWED", "업로드 part를 받을 수 없습니다.");
    }
    long expectedMax = partNumber == session.getExpectedPartCount()
        ? session.getDeclaredSizeBytes()
            - properties.getUploadPartSizeBytes() * (partNumber - 1)
        : properties.getUploadPartSizeBytes();
    if (contentLength < 1 || contentLength > expectedMax
        || partNumber < session.getExpectedPartCount()
            && contentLength != properties.getUploadPartSizeBytes()) {
      throw api(HttpStatus.BAD_REQUEST, "UPLOAD_PART_SIZE", "part 크기가 올바르지 않습니다.");
    }
    return storage.uploadLocalPart(
        sessionId, session.getMultipartUploadId(), partNumber, body, contentLength);
  }

  public CompleteResult complete(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String sessionId,
      CompleteRequest request) {
    Academy academy = academyService.requireMyAcademy(principal);
    Manifest manifest = canonicalManifest(request.parts());
    PhaseA phase = transactions.execute(status -> phaseA(academy.getAcademyId(), sessionId, manifest));
    if (phase == null) throw new IllegalStateException("phase A returned null");
    if (!phase.runExternalWork()) {
      return phase.result();
    }
    return executePhaseB(phase.snapshot(), manifest.parts());
  }

  public CompleteResult abort(
      JwtTokenProvider.AuthenticatedPrincipal principal, String sessionId) {
    Academy academy = academyService.requireMyAcademy(principal);
    AbortSnapshot snapshot = transactions.execute(status -> {
      AcademyVideoUploadSession session = locked(sessionId, academy.getAcademyId());
      LocalDateTime now = sessionRepository.databaseNow();
      String invalidatedToken = session.getFinalizationToken();
      boolean changed = session.abort(now);
      if (changed) {
        enqueueTerminalCleanup(session, "ABORTED", now);
        recordTerminalAfterCommit(session, now);
      }
      return new AbortSnapshot(
          session.getSessionId(), session.getStagingKey(), session.getMultipartUploadId(),
          invalidatedToken, sessionResponse(session, null));
    });
    if (snapshot == null) throw new IllegalStateException("abort transaction returned null");
    cancellation.signal(snapshot.sessionId(), snapshot.invalidatedToken());
    if (snapshot.response().state() == VideoUploadSessionState.ABORTED) {
      storage.abort(snapshot.stagingKey(), snapshot.uploadId());
    }
    return new CompleteResult(
        snapshot.response(),
        snapshot.response().state() == VideoUploadSessionState.ACCEPTED
            ? HttpStatus.OK : HttpStatus.NO_CONTENT);
  }

  public SessionObservation observe(
      JwtTokenProvider.AuthenticatedPrincipal principal, String sessionId) {
    Academy academy = academyService.requireMyAcademy(principal);
    AcademyVideoUploadSession session = sessionRepository
        .findBySessionIdAndAcademy_AcademyId(sessionId, academy.getAcademyId())
        .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "UPLOAD_SESSION_NOT_FOUND",
            "업로드 세션을 찾을 수 없습니다."));
    LocalDateTime serverNow = sessionRepository.databaseNow();
    return new SessionObservation(
        session.getSessionId(), session.getState(),
        session.getAcceptedVideo() == null ? null : session.getAcceptedVideo().getVideoId(),
        session.getLastErrorCode(),
        session.getState() == VideoUploadSessionState.COMPLETING
            ? Math.toIntExact(properties.getUploadRecoveryRetryMs() / 1_000) : null,
        serverNow, session.getDeadlineAt());
  }

  @Scheduled(fixedDelayString = "#{@academyVideoUploadProperties.uploadRecoveryPollMs}")
  public void recoverDueFinalizations() {
    expireAbandonedSessions();
    LocalDateTime now = sessionRepository.databaseNow();
    for (AcademyVideoUploadSession candidate :
        sessionRepository
            .findTop100ByStateAndNextFinalizeAttemptAtLessThanEqualOrderByNextFinalizeAttemptAt(
                VideoUploadSessionState.COMPLETING, now)) {
      if (cancellation.isActive(
          candidate.getSessionId(), candidate.getFinalizationToken())) {
        continue;
      }
      RecoveryClaim claim = transactions.execute(status -> claimRecovery(candidate.getSessionId()));
      if (claim != null) {
        executePhaseB(claim.snapshot(), parsePersistedManifest(claim.snapshot().manifestJson()));
      }
    }
  }

  public void expireAbandonedSessions() {
    LocalDateTime now = sessionRepository.databaseNow();
    List<AcademyVideoUploadSession> expired =
        sessionRepository.findTop100ByStateInAndDeadlineAtLessThanOrderByDeadlineAt(
            LIVE_RESERVATIONS, now);
    for (AcademyVideoUploadSession candidate : expired) {
      AbortSnapshot snapshot = transactions.execute(status -> {
        AcademyVideoUploadSession session = sessionRepository
            .findWithLockBySessionIdAndAcademyId(
                candidate.getSessionId(), candidate.getAcademy().getAcademyId())
            .orElse(null);
        if (session == null) return null;
        LocalDateTime databaseNow = sessionRepository.databaseNow();
        if (session.getState() != VideoUploadSessionState.INITIATED
            && session.getState() != VideoUploadSessionState.COMPLETING
            || !databaseNow.isAfter(session.getDeadlineAt())) {
          return null;
        }
        String invalidatedToken = session.getFinalizationToken();
        session.expire(databaseNow);
        enqueueTerminalCleanup(session, "EXPIRED", databaseNow);
        recordTerminalAfterCommit(session, databaseNow);
        return new AbortSnapshot(
            session.getSessionId(), session.getStagingKey(), session.getMultipartUploadId(),
            invalidatedToken, sessionResponse(session, null));
      });
      if (snapshot != null) {
        cancellation.signal(snapshot.sessionId(), snapshot.invalidatedToken());
        storage.abort(snapshot.stagingKey(), snapshot.uploadId());
      }
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverAfterRestart() {
    rawValidator.sweepStale(identity -> sessionRepository.findById(identity.sessionId())
        .filter(session -> session.getState() == VideoUploadSessionState.COMPLETING)
        .map(AcademyVideoUploadSession::getFinalizationToken)
        .filter(identity.token()::equals)
        .isPresent());
    recoverDueFinalizations();
  }

  private PhaseA phaseA(Long academyId, String sessionId, Manifest manifest) {
    AcademyVideoUploadSession session = locked(sessionId, academyId);
    LocalDateTime now = sessionRepository.databaseNow();
    if (session.getState() == VideoUploadSessionState.ACCEPTED) {
      return PhaseA.result(new CompleteResult(sessionResponse(session, null), HttpStatus.OK));
    }
    if (session.getState() == VideoUploadSessionState.COMPLETING) {
      if (!manifest.hash().equals(session.getManifestHash())) {
        throw api(HttpStatus.CONFLICT, "UPLOAD_MANIFEST_CONFLICT",
            "이미 확정된 part manifest와 다릅니다.");
      }
      int retrySeconds = Math.toIntExact(properties.getUploadRecoveryRetryMs() / 1_000);
      return PhaseA.result(new CompleteResult(
          sessionResponse(session, retrySeconds), HttpStatus.ACCEPTED));
    }
    if (session.getState() != VideoUploadSessionState.INITIATED) {
      return PhaseA.result(new CompleteResult(sessionResponse(session, null), terminalStatus(session)));
    }
    if (now.isAfter(session.getDeadlineAt())) {
      session.expire(now);
      enqueueTerminalCleanup(session, "EXPIRED", now);
      recordTerminalAfterCommit(session, now);
      return PhaseA.result(new CompleteResult(sessionResponse(session, null), HttpStatus.REQUEST_TIMEOUT));
    }
    String token = UUID.randomUUID().toString();
    session.beginFinalization(token, manifest.json(), manifest.hash(), now);
    return PhaseA.external(snapshot(session, now));
  }

  private CompleteResult executePhaseB(SessionSnapshot snapshot, List<CompletedPart> manifest) {
    Duration initialRemaining = Duration.between(snapshot.databaseNow(), snapshot.deadlineAt());
    UploadWorkBudget budget = UploadWorkBudget.of(initialRemaining);
    try (AcademyVideoFinalizationCancellation.Handle handle =
             cancellation.register(snapshot.sessionId(), snapshot.token());
         RecoveryHeartbeat heartbeat = startRecoveryHeartbeat(snapshot)) {
      BooleanSupplier cancelled = durableCancellation(snapshot, handle, heartbeat);
      AcademyVideoUploadStorage.StorageState state =
          storage.inspect(snapshot.stagingKey(), snapshot.uploadId(), requireRemaining(budget));
      if (state.multipartOpen()) {
        var completed = storage.complete(
            snapshot.stagingKey(), snapshot.uploadId(),
            manifest.stream().map(part -> new AcademyVideoUploadStorage.Part(
                part.partNumber(), part.eTag(), part.checksum())).toList(),
            requireRemaining(budget));
        persistCompletedObject(snapshot, completed.eTag(), completed.sizeBytes());
      } else if (!state.objectPresent()) {
        return permanentFailure(snapshot, "COMPLETED_OBJECT_MISSING", HttpStatus.UNPROCESSABLE_ENTITY);
      }
      AcademyVideoUploadStorage.ObjectMetadata metadata =
          storage.head(snapshot.stagingKey(), requireRemaining(budget));
      if (metadata.sizeBytes() < 1 || metadata.sizeBytes() > properties.getMaxRawBytes()
          || metadata.sizeBytes() != snapshot.declaredSizeBytes()) {
        return permanentFailure(snapshot, "COMPLETED_SIZE_MISMATCH",
            HttpStatus.UNPROCESSABLE_ENTITY);
      }
      persistCompletedObject(snapshot, metadata.eTag(), metadata.sizeBytes());
      AcademyVideoRawValidator.ValidatedRaw raw = rawValidator.validate(
          snapshot.sessionId(), snapshot.token(), snapshot.stagingKey(), metadata.sizeBytes(),
          budget, cancelled);
      return accept(snapshot, raw);
    } catch (AcademyVideoStorageException e) {
      if ("MANIFEST_MISMATCH".equals(e.getMessage())
          || "MANIFEST_INVALID".equals(e.getMessage())
          || "PART_MISSING".equals(e.getMessage())) {
        return permanentFailure(snapshot, "UPLOAD_MANIFEST_INVALID", HttpStatus.CONFLICT);
      }
      return transientFailure(snapshot, "STORAGE_TRANSIENT");
    } catch (AcademyVideoRawValidationException e) {
      if (!e.transientFailure()) {
        return permanentFailure(snapshot, e.errorCode(), HttpStatus.UNPROCESSABLE_ENTITY);
      }
      return transientFailure(snapshot, e.errorCode());
    } catch (RuntimeException e) {
      return transientFailure(snapshot, "FINALIZATION_TRANSIENT");
    }
  }

  private CompleteResult accept(
      SessionSnapshot snapshot, AcademyVideoRawValidator.ValidatedRaw raw) {
    CompleteResult result = transactions.execute(status -> {
      AcademyVideoUploadSession session = sessionRepository
          .findWithLockBySessionIdAndAcademyId(snapshot.sessionId(), snapshot.academyId())
          .orElseThrow();
      LocalDateTime now = sessionRepository.databaseNow();
      if (session.getState() == VideoUploadSessionState.ACCEPTED) {
        return new CompleteResult(sessionResponse(session, null), HttpStatus.OK);
      }
      if (session.getState() != VideoUploadSessionState.COMPLETING
          || !fenceMatches(session, snapshot, now)) {
        return new CompleteResult(sessionResponse(session, null), terminalStatus(session));
      }
      if (now.isAfter(session.getDeadlineAt())) {
        session.expire(now);
        enqueueTerminalCleanup(session, "EXPIRED", now);
        recordTerminalAfterCommit(session, now);
        return new CompleteResult(sessionResponse(session, null), HttpStatus.REQUEST_TIMEOUT);
      }
      AcademyVideo video = videoRepository.save(AcademyVideo.readyRaw(
          session.getAcademy(), session.getRequestedFilename(), session.getStagingKey(),
          raw.mediaType(), raw.sizeBytes(), raw.durationMs(), raw.width(), raw.height()));
      session.accept(video, now);
      recordTerminalAfterCommit(session, now);
      // Force every Phase C write through the database before the transaction can return an
      // accepted response. A deferred flush/commit failure must roll back video, job and session.
      sessionRepository.flush();
      return new CompleteResult(sessionResponse(session, null), HttpStatus.OK);
    });
    if (result == null) throw new IllegalStateException("phase C returned null");
    return result;
  }

  private CompleteResult permanentFailure(
      SessionSnapshot snapshot, String code, HttpStatus status) {
    CompleteResult result = transactions.execute(tx -> {
      AcademyVideoUploadSession session = sessionRepository
          .findWithLockBySessionIdAndAcademyId(snapshot.sessionId(), snapshot.academyId())
          .orElseThrow();
      if (session.getState() == VideoUploadSessionState.COMPLETING
          && fenceMatches(session, snapshot, sessionRepository.databaseNow())) {
        LocalDateTime now = sessionRepository.databaseNow();
        session.reject(code, now);
        enqueueTerminalCleanup(session, code, now);
        recordTerminalAfterCommit(session, now);
      }
      return new CompleteResult(sessionResponse(session, null), status);
    });
    return result == null ? new CompleteResult(
        new SessionResponse(snapshot.sessionId(), VideoUploadSessionState.REJECTED,
            null, code, null), status) : result;
  }

  private CompleteResult transientFailure(SessionSnapshot snapshot, String code) {
    CompleteResult result = transactions.execute(tx -> {
      AcademyVideoUploadSession session = sessionRepository
          .findWithLockBySessionIdAndAcademyId(snapshot.sessionId(), snapshot.academyId())
          .orElseThrow();
      LocalDateTime now = sessionRepository.databaseNow();
      if (session.getState() != VideoUploadSessionState.COMPLETING
          || !fenceMatches(session, snapshot, now)) {
        return new CompleteResult(sessionResponse(session, null), terminalStatus(session));
      }
      if (now.isAfter(session.getDeadlineAt())) {
        session.expire(now);
        enqueueTerminalCleanup(session, "EXPIRED", now);
        recordTerminalAfterCommit(session, now);
        return new CompleteResult(sessionResponse(session, null), HttpStatus.REQUEST_TIMEOUT);
      }
      int retrySeconds = Math.toIntExact(properties.getUploadRecoveryRetryMs() / 1_000);
      session.scheduleRecovery(code, now.plusNanos(properties.getUploadRecoveryRetryMs() * 1_000_000));
      return new CompleteResult(
          sessionResponse(session, retrySeconds), HttpStatus.SERVICE_UNAVAILABLE);
    });
    if (result == null) throw new IllegalStateException("transient transition returned null");
    return result;
  }

  private RecoveryClaim claimRecovery(String sessionId) {
    AcademyVideoUploadSession session = sessionRepository.findById(sessionId).orElse(null);
    if (session == null) return null;
    session = sessionRepository
        .findWithLockBySessionIdAndAcademyId(sessionId, session.getAcademy().getAcademyId())
        .orElse(null);
    if (session == null) return null;
    LocalDateTime now = sessionRepository.databaseNow();
    if (!session.claimRecovery(
        recoveryOwner, now,
        now.plusNanos(properties.getUploadRecoveryLeaseMs() * 1_000_000))) {
      return null;
    }
    return new RecoveryClaim(snapshot(session, now));
  }

  private void persistCompletedObject(SessionSnapshot snapshot, String eTag, long size) {
    transactions.executeWithoutResult(tx -> sessionRepository
        .findWithLockBySessionIdAndAcademyId(snapshot.sessionId(), snapshot.academyId())
        .filter(session -> session.getState() == VideoUploadSessionState.COMPLETING)
        .filter(session -> fenceMatches(session, snapshot, sessionRepository.databaseNow()))
        .ifPresent(session -> session.recordCompletedObject(eTag, size)));
  }

  private BooleanSupplier durableCancellation(
      SessionSnapshot snapshot,
      AcademyVideoFinalizationCancellation.Handle handle,
      RecoveryHeartbeat heartbeat) {
    AtomicLong nextPoll = new AtomicLong(0);
    return () -> {
      if (handle.cancelled() || heartbeat.leaseLost()) return true;
      long now = System.nanoTime();
      if (now < nextPoll.get()) return false;
      nextPoll.set(now + Duration.ofSeconds(2).toNanos());
      return sessionRepository.findById(snapshot.sessionId())
          .map(session -> !fenceMatches(session, snapshot, sessionRepository.databaseNow()))
          .orElse(true);
    };
  }

  private RecoveryHeartbeat startRecoveryHeartbeat(SessionSnapshot snapshot) {
    if (snapshot.recoveryOwner() == null) {
      return RecoveryHeartbeat.noop();
    }
    java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean();
    java.util.concurrent.atomic.AtomicBoolean leaseLost =
        new java.util.concurrent.atomic.AtomicBoolean();
    Thread thread = Thread.ofVirtual().name("academy-video-upload-recovery-heartbeat").start(() -> {
      while (!stopped.get()) {
        try {
          Thread.sleep(properties.getUploadRecoveryHeartbeatMs());
        } catch (InterruptedException e) {
          return;
        }
        boolean renewed = Boolean.TRUE.equals(transactions.execute(status -> {
          AcademyVideoUploadSession session = sessionRepository
              .findWithLockBySessionIdAndAcademyId(snapshot.sessionId(), snapshot.academyId())
              .orElse(null);
          if (session == null
              || !constantTimeEquals(snapshot.token(), session.getFinalizationToken())) {
            return false;
          }
          LocalDateTime now = sessionRepository.databaseNow();
          return session.renewRecoveryLease(
              snapshot.recoveryOwner(), now,
              now.plusNanos(properties.getUploadRecoveryLeaseMs() * 1_000_000));
        }));
        if (!renewed) {
          leaseLost.set(true);
          return;
        }
      }
    });
    return new RecoveryHeartbeat(stopped, leaseLost, thread);
  }

  private void enqueueTerminalCleanup(
      AcademyVideoUploadSession session, String reason, LocalDateTime now) {
    enqueueCleanup(session, VideoAssetClass.MULTIPART_UPLOAD,
        session.getMultipartUploadId(), reason, now);
    enqueueCleanup(session, VideoAssetClass.STAGED_OBJECT,
        session.getStagingKey(), reason, now);
  }

  private void recordTerminalAfterCommit(
      AcademyVideoUploadSession session, LocalDateTime terminalAt) {
    VideoUploadSessionState outcome = session.getState();
    LocalDateTime startedAt = session.getCreatedAt();
    Duration elapsed = startedAt == null || terminalAt == null
        ? Duration.ZERO : Duration.between(startedAt, terminalAt);
    String sessionId = session.getSessionId();
    AcademyVideoMetrics.afterCommit(() -> {
      metrics.sessionTerminal(outcome, elapsed);
      log.info("academy_video_upload sessionId={} stage=terminal elapsedMs={} outcome={}",
          sessionId, Math.max(0, elapsed.toMillis()), outcome);
    });
  }

  private void enqueueCleanup(
      AcademyVideoUploadSession session,
      VideoAssetClass assetClass,
      String assetKey,
      String reason,
      LocalDateTime now) {
    String dedupe = sha256(assetClass.name() + "\n" + assetKey + "\n" + reason);
    cleanupRepository.insertPendingIgnoreDuplicate(
        dedupe, session.getSessionId(), null, null, assetClass.name(), assetKey, reason, now);
  }

  private AcademyVideoUploadSession locked(String sessionId, Long academyId) {
    return sessionRepository.findWithLockBySessionIdAndAcademyId(sessionId, academyId)
        .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "UPLOAD_SESSION_NOT_FOUND",
            "업로드 세션을 찾을 수 없습니다."));
  }

  private void assertQuotaAvailable(Long academyId) {
    academyRepository.findForUpdateByAcademyId(academyId)
        .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "ACADEMY_NOT_FOUND",
            "Academy를 찾을 수 없습니다."));
    LocalDateTime now = sessionRepository.databaseNow();
    long videos = videoRepository.countByAcademy_AcademyIdAndLifecycleStatusNot(
        academyId, VideoLifecycleStatus.DELETING);
    long reservations =
        sessionRepository.countByAcademy_AcademyIdAndStateInAndDeadlineAtGreaterThanEqual(
            academyId, LIVE_RESERVATIONS, now);
    if (videos + reservations >= properties.getUploadMaxCount()) {
      throw api(HttpStatus.BAD_REQUEST, "ACADEMY_VIDEO_QUOTA",
          "영상은 학원당 최대 " + properties.getUploadMaxCount() + "개까지 등록할 수 있습니다.");
    }
  }

  private static Manifest canonicalManifest(List<CompletedPart> input) {
    if (input == null || input.isEmpty() || input.size() > 10) {
      throw api(HttpStatus.BAD_REQUEST, "UPLOAD_MANIFEST_INVALID",
          "part manifest가 올바르지 않습니다.");
    }
    StringBuilder json = new StringBuilder("[");
    int expected = 1;
    for (CompletedPart part : input) {
      if (part == null || part.partNumber() != expected++
          || part.eTag() == null || part.eTag().isBlank() || part.eTag().length() > 255
          || part.checksum() != null && part.checksum().length() > 255) {
        throw api(HttpStatus.BAD_REQUEST, "UPLOAD_MANIFEST_INVALID",
            "part manifest가 올바르지 않습니다.");
      }
      if (json.length() > 1) json.append(',');
      json.append("{\"partNumber\":").append(part.partNumber())
          .append(",\"eTag\":\"").append(escape(part.eTag())).append("\"");
      if (part.checksum() != null) {
        json.append(",\"checksum\":\"").append(escape(part.checksum())).append("\"");
      }
      json.append('}');
    }
    json.append(']');
    String canonical = json.toString();
    return new Manifest(List.copyOf(input), canonical, sha256(canonical));
  }

  private List<CompletedPart> parsePersistedManifest(String json) {
    try {
      List<CompletedPart> parts = objectMapper.readValue(
          json,
          objectMapper.getTypeFactory().constructCollectionType(
              List.class, CompletedPart.class));
      Manifest canonical = canonicalManifest(parts);
      return canonical.parts();
    } catch (Exception e) {
      throw new IllegalStateException("persisted manifest is invalid", e);
    }
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String safeFilename(String value) {
    String filename = value == null ? "" : value.replace('\\', '/');
    filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
    if (filename.isBlank() || filename.length() > 255 || filename.indexOf('\0') >= 0) {
      throw api(HttpStatus.BAD_REQUEST, "INVALID_FILENAME", "파일명이 올바르지 않습니다.");
    }
    return filename;
  }

  private static String extension(String filename) {
    int separator = filename.lastIndexOf('.');
    return separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private static boolean constantTimeEquals(String left, String right) {
    if (left == null || right == null) return left == right;
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private static Duration requireRemaining(UploadWorkBudget budget) {
    Duration remaining = budget.remaining();
    if (remaining.isZero()) {
      throw new AcademyVideoRawValidationException("DEADLINE_EXCEEDED", true);
    }
    return remaining;
  }

  private static HttpStatus terminalStatus(AcademyVideoUploadSession session) {
    return switch (session.getState()) {
      case ACCEPTED -> HttpStatus.OK;
      case EXPIRED -> HttpStatus.REQUEST_TIMEOUT;
      case REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
      case ABORTED -> HttpStatus.CONFLICT;
      case COMPLETING -> HttpStatus.ACCEPTED;
      case INITIATED -> HttpStatus.CONFLICT;
    };
  }

  private static SessionResponse sessionResponse(
      AcademyVideoUploadSession session, Integer retryAfter) {
    return new SessionResponse(
        session.getSessionId(), session.getState(),
        session.getAcceptedVideo() == null ? null : session.getAcceptedVideo().getVideoId(),
        session.getLastErrorCode(), retryAfter);
  }

  private static SessionSnapshot snapshot(
      AcademyVideoUploadSession session, LocalDateTime databaseNow) {
    return new SessionSnapshot(
        session.getSessionId(), session.getAcademy().getAcademyId(),
        session.getRequestedFilename(), session.getDeclaredSizeBytes(),
        session.getStagingKey(), session.getMultipartUploadId(),
        session.getFinalizationToken(), session.getPartManifestJson(),
        session.getDeadlineAt(), databaseNow, session.getFinalizeRecoveryOwner());
  }

  private static boolean fenceMatches(
      AcademyVideoUploadSession session, SessionSnapshot snapshot, LocalDateTime now) {
    if (!constantTimeEquals(snapshot.token(), session.getFinalizationToken())) {
      return false;
    }
    return snapshot.recoveryOwner() == null
        ? session.getFinalizeRecoveryOwner() == null
        : session.isRecoveryOwnedBy(snapshot.recoveryOwner(), now);
  }

  private static AcademyVideoUploadApiException api(
      HttpStatus status, String code, String message) {
    return new AcademyVideoUploadApiException(status, code, message, null);
  }

  public record CompleteResult(SessionResponse response, HttpStatus status) {}
  private record Manifest(List<CompletedPart> parts, String json, String hash) {}
  private record PhaseA(SessionSnapshot snapshot, CompleteResult result) {
    static PhaseA external(SessionSnapshot snapshot) { return new PhaseA(snapshot, null); }
    static PhaseA result(CompleteResult result) { return new PhaseA(null, result); }
    boolean runExternalWork() { return snapshot != null; }
  }
  private record SessionSnapshot(
      String sessionId, Long academyId, String filename, long declaredSizeBytes,
      String stagingKey, String uploadId, String token, String manifestJson,
      LocalDateTime deadlineAt, LocalDateTime databaseNow, String recoveryOwner) {}
  private record AbortSnapshot(
      String sessionId, String stagingKey, String uploadId, String invalidatedToken,
      SessionResponse response) {}
  private record RecoveryClaim(SessionSnapshot snapshot) {}

  private static final class RecoveryHeartbeat implements AutoCloseable {
    private final java.util.concurrent.atomic.AtomicBoolean stopped;
    private final java.util.concurrent.atomic.AtomicBoolean leaseLost;
    private final Thread thread;

    private RecoveryHeartbeat(
        java.util.concurrent.atomic.AtomicBoolean stopped,
        java.util.concurrent.atomic.AtomicBoolean leaseLost,
        Thread thread) {
      this.stopped = stopped;
      this.leaseLost = leaseLost;
      this.thread = thread;
    }

    static RecoveryHeartbeat noop() {
      return new RecoveryHeartbeat(
          new java.util.concurrent.atomic.AtomicBoolean(),
          new java.util.concurrent.atomic.AtomicBoolean(), null);
    }

    boolean leaseLost() {
      return leaseLost.get();
    }

    @Override
    public void close() {
      stopped.set(true);
      if (thread != null) thread.interrupt();
    }
  }
}
