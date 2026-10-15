package f2r.tutor_solution.private_tutor_solution_back.domain.landing.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VideoUploadRejectedException;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoAdminResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoRenderItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoSourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.repository.LandingVideoSlotRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.StorageReadLimitExceededException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.TranscodeResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoProbe;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@Service
public class LandingVideoService {

  private static final String VIDEO_FILE_TYPE = "VIDEO";
  private static final String IMAGE_FILE_TYPE = "IMAGE";
  private static final Set<String> SUPPORTED_INPUT_CODECS =
      Set.of("h264", "hevc", "vp8", "vp9", "av1", "mpeg4");

  private final LandingVideoSlotRepository repository;
  private final LandingVideoSlotTransaction transactions;
  private final FileStorage fileStorage;
  private final VideoTranscoder videoTranscoder;
  private final int maxDurationSec;
  private final long maxUploadBytes;
  private final long maxOutputBytes;
  private final long maxThumbnailBytes;
  private final Semaphore servePermits;

  public LandingVideoService(
      LandingVideoSlotRepository repository,
      LandingVideoSlotTransaction transactions,
      FileStorage fileStorage,
      VideoTranscoder videoTranscoder,
      @Value("${app.landing-video.max-duration-sec:60}") int maxDurationSec,
      @Value("${app.landing-video.max-upload-bytes:104857600}") long maxUploadBytes,
      @Value("${app.landing-video.max-output-bytes:31457280}") long maxOutputBytes,
      @Value("${app.landing-video.max-thumbnail-bytes:2097152}") long maxThumbnailBytes,
      @Value("${app.landing-video.serve-permits:3}") int servePermits) {
    this.repository = repository;
    this.transactions = transactions;
    this.fileStorage = fileStorage;
    this.videoTranscoder = videoTranscoder;
    this.maxDurationSec = maxDurationSec;
    this.maxUploadBytes = maxUploadBytes;
    this.maxOutputBytes = maxOutputBytes;
    this.maxThumbnailBytes = maxThumbnailBytes;
    this.servePermits = new Semaphore(servePermits);
  }

  public List<LandingVideoAdminResponse> listAdmin() {
    Map<LandingVideoSlotKey, LandingVideoSlot> found = new EnumMap<>(LandingVideoSlotKey.class);
    repository.findAll().forEach(entity -> found.put(entity.getSlot(), entity));
    return LandingVideoSlotKey.displayOrder().stream()
        .map(slot -> {
          LandingVideoSlot entity = found.get(slot);
          return entity == null
              ? LandingVideoAdminResponse.bundled(slot, true)
              : toAdminResponse(LandingVideoSlotState.from(entity));
        })
        .toList();
  }

  public List<LandingVideoRenderItem> resolveRenderItems() {
    Map<LandingVideoSlotKey, LandingVideoSlot> found = new EnumMap<>(LandingVideoSlotKey.class);
    repository.findAll().forEach(entity -> found.put(entity.getSlot(), entity));
    return LandingVideoSlotKey.displayOrder().stream()
        .map(slot -> toRenderItem(slot, found.get(slot)))
        .toList();
  }

  public LandingVideoAdminResponse updateVisibility(
      LandingVideoSlotKey slot, boolean visible) {
    return toAdminResponse(transactions.updateVisibility(slot, visible));
  }

  public LandingVideoAdminResponse upload(
      LandingVideoSlotKey slot,
      JwtTokenProvider.AuthenticatedPrincipal principal,
      MultipartFile file) {
    if (principal == null || principal.userId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    validateFile(file);

    Path workDir = null;
    try {
      workDir = Files.createTempDirectory("landing-video-");
      Path input = workDir.resolve("input" + extensionOf(file.getOriginalFilename()));
      file.transferTo(input);

      VideoProbe probe = videoTranscoder.probe(input);
      validateProbe(probe);
      TranscodeResult result = videoTranscoder.transcode(input, probe, workDir);
      VideoProbe normalizedProbe = videoTranscoder.probe(result.mp4());
      validateDuration(normalizedProbe.durationSec());
      int normalizedDurationSec = persistedDurationSec(normalizedProbe.durationSec());
      long actualOutputBytes = Files.size(result.mp4());
      if (actualOutputBytes <= 0 || actualOutputBytes > maxOutputBytes) {
        throw rejected("변환된 영상이 너무 큽니다. 더 짧거나 작은 영상을 올려 주세요.");
      }
      long thumbnailBytes = Files.size(result.thumbnail());
      if (thumbnailBytes <= 0 || thumbnailBytes > maxThumbnailBytes) {
        throw rejected("변환된 영상의 썸네일이 너무 큽니다.");
      }
      return persist(
          slot,
          principal.userId(),
          file.getOriginalFilename(),
          result,
          normalizedProbe,
          normalizedDurationSec,
          actualOutputBytes);
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "랜딩 영상을 처리할 수 없습니다.", e);
    } finally {
      deleteRecursively(workDir);
    }
  }

  private LandingVideoAdminResponse persist(
      LandingVideoSlotKey slot,
      Long userId,
      String originalFilename,
      TranscodeResult result,
      VideoProbe normalizedProbe,
      int normalizedDurationSec,
      long actualOutputBytes)
      throws IOException {
    String newVideoKey = null;
    String newThumbnailKey = null;
    LandingVideoSlotTransaction.Replacement replacement;
    try {
      try (InputStream input = Files.newInputStream(result.mp4())) {
        newVideoKey =
            fileStorage.saveRaw(userId, "landing-video.mp4", input, actualOutputBytes);
      }
      newThumbnailKey =
          fileStorage.saveImage(
              userId, "landing-video-thumbnail", Files.readAllBytes(result.thumbnail()));

      try {
        replacement =
            transactions.replace(
                slot,
                originalFilename,
                newVideoKey,
                newThumbnailKey,
                normalizedDurationSec,
                actualOutputBytes,
                normalizedProbe.width(),
                normalizedProbe.height());
      } catch (DataIntegrityViolationException insertRace) {
        replacement =
            transactions.replaceAfterInsertRace(
                slot,
                originalFilename,
                newVideoKey,
                newThumbnailKey,
                normalizedDurationSec,
                actualOutputBytes,
                normalizedProbe.width(),
                normalizedProbe.height());
      }
    } catch (RuntimeException | IOException e) {
      deleteReported(slot, newVideoKey, VIDEO_FILE_TYPE, "new-video-compensation");
      deleteReported(slot, newThumbnailKey, IMAGE_FILE_TYPE, "new-thumbnail-compensation");
      throw e;
    }

    // The transactional proxy returns only after commit. Cleanup is intentionally outside the
    // compensation catch: a best-effort old-file cleanup problem must never delete the committed
    // new asset.
    cleanupReplaced(slot, replacement.previous(), newVideoKey, newThumbnailKey);
    return toAdminResponse(replacement.current());
  }

  public ManagedMedia readPublicVideo(LandingVideoSlotKey slot) {
    return readManaged(slot, false, false);
  }

  public ManagedMedia readAdminVideo(LandingVideoSlotKey slot) {
    return readManaged(slot, true, false);
  }

  public ManagedMedia readAdminThumbnail(LandingVideoSlotKey slot) {
    return readManaged(slot, true, true);
  }

  private ManagedMedia readManaged(
      LandingVideoSlotKey slot, boolean admin, boolean thumbnail) {
    if (!servePermits.tryAcquire()) {
      log.warn("landing video serve rejected: slot={}, reason=permit-saturated", slot);
      throw unavailable("영상 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    }

    try {
      LandingVideoSlotState initial = loadReadableState(slot, admin, thumbnail);
      byte[] bytes;
      try {
        bytes = readAndValidate(slot, initial, thumbnail);
      } catch (IOException firstFailure) {
        LandingVideoSlotState latest = loadReadableState(slot, admin, thumbnail);
        String firstKey = thumbnail ? initial.thumbnailKey() : initial.storageKey();
        String latestKey = thumbnail ? latest.thumbnailKey() : latest.storageKey();
        if (firstKey.equals(latestKey)) {
          throw firstFailure;
        }
        try {
          bytes = readAndValidate(slot, latest, thumbnail);
        } catch (IOException retryFailure) {
          log.warn("landing video read rejected: slot={}, reason=retry-failed", slot);
          throw retryFailure;
        }
      }
      return new ManagedMedia(
          thumbnail ? MediaType.IMAGE_JPEG : MediaType.parseMediaType("video/mp4"),
          bytes,
          servePermits);
    } catch (ResponseStatusException e) {
      servePermits.release();
      throw e;
    } catch (StorageReadLimitExceededException e) {
      servePermits.release();
      log.warn("landing video read rejected: slot={}, reason=storage-limit", slot);
      throw unavailable("영상 파일을 안전하게 읽을 수 없습니다.");
    } catch (IOException e) {
      servePermits.release();
      log.warn("landing video read rejected: slot={}, reason=storage-read", slot);
      throw unavailable("영상 파일을 읽을 수 없습니다.");
    } catch (RuntimeException e) {
      servePermits.release();
      throw e;
    }
  }

  private LandingVideoSlotState loadReadableState(
      LandingVideoSlotKey slot, boolean admin, boolean thumbnail) {
    LandingVideoSlotState state =
        repository
            .findById(slot)
            .map(LandingVideoSlotState::from)
            .orElseThrow(() -> notFound("관리 중인 영상이 없습니다."));
    if (!admin && !state.visible()) {
      throw notFound("공개 중인 영상이 없습니다.");
    }
    if (!state.hasManagedAsset()) {
      throw notFound("관리 중인 영상이 없습니다.");
    }
    if (thumbnail && (state.thumbnailKey() == null || state.thumbnailKey().isBlank())) {
      throw notFound("썸네일이 없습니다.");
    }
    return state;
  }

  private byte[] readAndValidate(
      LandingVideoSlotKey slot, LandingVideoSlotState state, boolean thumbnail)
      throws IOException {
    String key = thumbnail ? state.thumbnailKey() : state.storageKey();
    long limit = thumbnail ? maxThumbnailBytes : maxOutputBytes;
    if (!thumbnail
        && (state.sizeBytes() == null
            || state.sizeBytes() < 0
            || state.sizeBytes() > maxOutputBytes)) {
      log.warn("landing video read rejected: slot={}, reason=persisted-size", slot);
      throw unavailable("영상 파일 크기를 확인할 수 없습니다.");
    }

    byte[] bytes =
        fileStorage.readSingleLimited(
            key, thumbnail ? IMAGE_FILE_TYPE : VIDEO_FILE_TYPE, limit);
    if (!thumbnail && bytes.length != state.sizeBytes()) {
      log.warn("landing video read rejected: slot={}, reason=size-mismatch", slot);
      throw unavailable("영상 파일 크기가 저장 정보와 다릅니다.");
    }
    return bytes;
  }

  private LandingVideoRenderItem toRenderItem(
      LandingVideoSlotKey slot, LandingVideoSlot entity) {
    if (entity == null) {
      return LandingVideoRenderItem.bundled(slot);
    }
    if (!entity.isVisible()) {
      return LandingVideoRenderItem.hidden(slot);
    }
    return entity.hasManagedAsset()
        ? LandingVideoRenderItem.managed(slot)
        : LandingVideoRenderItem.bundled(slot);
  }

  private LandingVideoAdminResponse toAdminResponse(LandingVideoSlotState state) {
    if (!state.hasManagedAsset()) {
      return LandingVideoAdminResponse.bundled(state.slot(), state.visible());
    }
    String base = "/api/admin/landing/videos/" + state.slot().name();
    return new LandingVideoAdminResponse(
        state.slot(),
        state.slot().getLabel(),
        state.slot().getDescription(),
        state.visible(),
        LandingVideoSourceType.MANAGED,
        true,
        state.originalFilename(),
        state.durationSec(),
        state.sizeBytes(),
        state.width(),
        state.height(),
        base + "/stream",
        state.thumbnailKey() == null || state.thumbnailKey().isBlank()
            ? null
            : base + "/thumbnail");
  }

  private void cleanupReplaced(
      LandingVideoSlotKey slot,
      LandingVideoSlotState previous,
      String newVideoKey,
      String newThumbnailKey) {
    if (!same(previous.storageKey(), newVideoKey)) {
      deleteReported(slot, previous.storageKey(), VIDEO_FILE_TYPE, "old-video-cleanup");
    }
    if (!same(previous.thumbnailKey(), newThumbnailKey)) {
      deleteReported(slot, previous.thumbnailKey(), IMAGE_FILE_TYPE, "old-thumbnail-cleanup");
    }
  }

  private void deleteReported(
      LandingVideoSlotKey slot, String key, String fileType, String reason) {
    boolean deleted;
    try {
      deleted = fileStorage.deleteReported(key, fileType);
    } catch (RuntimeException e) {
      deleted = false;
    }
    if (!deleted) {
      log.warn("landing video cleanup failed: slot={}, reason={}", slot, reason);
    }
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw rejected("업로드할 영상 파일이 없습니다.");
    }
    if (file.getSize() > maxUploadBytes) {
      throw rejected(
          "영상 용량은 " + (maxUploadBytes / (1024 * 1024)) + "MB를 넘을 수 없습니다.");
    }
  }

  private void validateProbe(VideoProbe probe) {
    validateDuration(probe.durationSec());
    if (probe.videoCodec() == null
        || !SUPPORTED_INPUT_CODECS.contains(probe.videoCodec().toLowerCase(Locale.ROOT))) {
      throw rejected("지원하지 않는 영상 형식입니다.");
    }
  }

  private void validateDuration(double durationSec) {
    if (!Double.isFinite(durationSec) || durationSec <= 0 || durationSec >= maxDurationSec) {
      throw rejected(
          "영상 길이는 0초보다 크고 " + maxDurationSec + "초 미만이어야 합니다.");
    }
  }

  private static int persistedDurationSec(double durationSec) {
    return Math.max(1, (int) Math.round(durationSec));
  }

  private void deleteRecursively(Path directory) {
    if (directory == null) {
      return;
    }
    boolean cleanupFailed = false;
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          cleanupFailed = true;
        }
      }
    } catch (IOException e) {
      cleanupFailed = true;
    }
    if (cleanupFailed) {
      log.warn("landing video temp cleanup failed");
    }
  }

  private static String extensionOf(String originalFilename) {
    if (originalFilename == null) {
      return "";
    }
    int dot = originalFilename.lastIndexOf('.');
    if (dot < 0 || dot == originalFilename.length() - 1) {
      return "";
    }
    String extension = originalFilename.substring(dot + 1);
    return extension.matches("[A-Za-z0-9]{1,5}")
        ? "." + extension.toLowerCase(Locale.ROOT)
        : "";
  }

  private static boolean same(String left, String right) {
    return left == null ? right == null : left.equals(right);
  }

  private static VideoUploadRejectedException rejected(String message) {
    return new VideoUploadRejectedException(message);
  }

  private static ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private static ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }

  public static final class ManagedMedia implements AutoCloseable {
    private final MediaType contentType;
    private final byte[] bytes;
    private final Semaphore permitOwner;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ManagedMedia(MediaType contentType, byte[] bytes, Semaphore permitOwner) {
      this.contentType = contentType;
      this.bytes = bytes;
      this.permitOwner = permitOwner;
    }

    public MediaType contentType() {
      return contentType;
    }

    public long contentLength() {
      return bytes.length;
    }

    public StreamingResponseBody body() {
      return this::writeTo;
    }

    private void writeTo(OutputStream output) throws IOException {
      try {
        output.write(bytes);
      } finally {
        close();
      }
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        permitOwner.release();
      }
    }
  }
}
