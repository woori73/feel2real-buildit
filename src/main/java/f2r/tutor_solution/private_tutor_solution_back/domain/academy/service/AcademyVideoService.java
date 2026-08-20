package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoOptimizationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.TranscodeResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoProbe;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

/**
 * 학원 홍보 영상의 업로드·조회·삭제.
 *
 * <p>업로드는 요청 스레드에서 ffmpeg 변환까지 끝내는 동기 처리다. 다만 인코딩과 S3 쓰기는 DB 트랜잭션
 * 밖에서 수행한다 — 수십 초짜리 인코딩이 커넥션을 붙들면 풀이 마른다. 업로드 경로가 DB에 닿는 것은
 * 마지막 row insert 하나뿐이라 여기에는 {@code @Transactional}을 두지 않는다(단건 save는 그 자체로
 * 원자적).
 *
 * <p><b>단, {@link #delete}는 예외다.</b> 수업상품 참조 검사가 들어오면서 read-then-write가 되어
 * 원자성이 필요해졌다. 그 메서드에만 {@code @Transactional}을 붙이고 참조 행에 락을 잡는다.
 * "insert 하나뿐이라 트랜잭션이 필요 없다"는 위 논리를 삭제 경로에 그대로 적용하면 안 된다.
 *
 * <p>파일과 DB row의 정합은 보상 삭제로 맞춘다. 저장 순서와 실패 처리는 아래를 따른다.
 * <ul>
 *   <li>생성: 변환 → S3 저장 → row insert. insert가 실패하면 caller가 S3 산출물을 지운다.</li>
 *   <li>삭제: row 삭제 → S3 삭제(best-effort). 반대로 하면 S3만 지워지고 row가 남아 재생이
 *       깨진 영상이 공개 페이지에 노출된다. 이 순서면 최악이라도 S3 고아만 남는다.</li>
 * </ul>
 */
@Slf4j
@Service
public class AcademyVideoService {

  /** FileStorage가 PDF 페이지 묶음과 단일 파일을 가르는 데만 쓰는 값. "PDF"가 아니면 단일 파일이다. */
  private static final String VIDEO_FILE_TYPE = "VIDEO";
  private static final String IMAGE_FILE_TYPE = "IMAGE";

  /** 폰 촬영본에서 실제로 나오는 코덱들. 이 밖은 변환을 시도하지 않고 거부한다. */
  private static final Set<String> SUPPORTED_INPUT_CODECS =
      Set.of("h264", "hevc", "vp8", "vp9", "av1", "mpeg4");

  private final AcademyService academyService;
  private final AcademyVideoRepository academyVideoRepository;
  private final AcademyVideoOptimizationJobRepository optimizationJobRepository;
  private final LessonMediaReferenceGuard lessonMediaReferenceGuard;
  private final AcademyVideoCleanupTransactions cleanupTransactions;
  private final FileStorage fileStorage;
  private final VideoTranscoder videoTranscoder;

  private final int maxDurationSec;
  private final long maxUploadBytes;
  private final long maxOutputBytes;
  private final int maxCount;

  public AcademyVideoService(
      AcademyService academyService,
      AcademyVideoRepository academyVideoRepository,
      AcademyVideoOptimizationJobRepository optimizationJobRepository,
      LessonMediaReferenceGuard lessonMediaReferenceGuard,
      AcademyVideoCleanupTransactions cleanupTransactions,
      FileStorage fileStorage,
      VideoTranscoder videoTranscoder,
      @Value("${app.video.max-duration-sec:60}") int maxDurationSec,
      @Value("${app.video.max-upload-bytes:104857600}") long maxUploadBytes,
      @Value("${app.video.max-output-bytes:104857600}") long maxOutputBytes,
      @Value("${app.video.max-count:25}") int maxCount) {
    this.academyService = academyService;
    this.academyVideoRepository = academyVideoRepository;
    this.optimizationJobRepository = optimizationJobRepository;
    this.lessonMediaReferenceGuard = lessonMediaReferenceGuard;
    this.cleanupTransactions = cleanupTransactions;
    this.fileStorage = fileStorage;
    this.videoTranscoder = videoTranscoder;
    this.maxDurationSec = maxDurationSec;
    this.maxUploadBytes = maxUploadBytes;
    this.maxOutputBytes = maxOutputBytes;
    this.maxCount = maxCount;
  }

  /**
   * 업로드 → 변환 → 저장. 클라이언트가 보낸 길이/용량은 신뢰하지 않고 ffprobe 결과로 재판정한다.
   */
  public AcademyVideoResponse upload(
      JwtTokenProvider.AuthenticatedPrincipal principal, MultipartFile file) {
    Academy academy = academyService.requireMyAcademy(principal);

    if (file == null || file.isEmpty()) {
      throw badRequest("업로드할 영상 파일이 없습니다.");
    }
    if (file.getSize() > maxUploadBytes) {
      throw badRequest("영상 용량은 " + toMegabytes(maxUploadBytes) + "MB를 넘을 수 없습니다.");
    }
    if (academyVideoRepository.countByAcademy_AcademyId(academy.getAcademyId()) >= maxCount) {
      throw badRequest("영상은 학원당 최대 " + maxCount + "개까지 등록할 수 있습니다.");
    }

    Path workDir;
    try {
      workDir = Files.createTempDirectory("academy-video-");
    } catch (IOException e) {
      throw serverError("영상 처리 작업 공간을 만들 수 없습니다.", e);
    }

    try {
      Path input = workDir.resolve("input" + extensionOf(file.getOriginalFilename()));
      file.transferTo(input);

      VideoProbe probe = videoTranscoder.probe(input);
      validateProbe(probe);

      TranscodeResult result = videoTranscoder.transcode(input, probe, workDir);
      if (result.sizeBytes() > maxOutputBytes) {
        throw badRequest("변환된 영상이 너무 큽니다. 더 짧거나 작은 영상을 올려 주세요.");
      }
      return persist(academy, file.getOriginalFilename(), result);
    } catch (IOException e) {
      throw serverError("영상 업로드 처리에 실패했습니다.", e);
    } finally {
      deleteRecursively(workDir);
    }
  }

  /**
   * 산출물을 저장소에 올리고 row를 만든다. 어느 단계에서 실패하든 이미 올라간 산출물을 되돌린다.
   *
   * <p>보상 삭제가 이 메서드 안에 있는 이유: row insert까지가 한 덩어리이고, 트랜잭션 경계를 별도로
   * 두지 않았기 때문이다. {@code @Transactional} 메서드였다면 commit이 메서드 리턴 뒤에 일어나므로
   * 보상 로직을 호출자 쪽으로 빼야 한다.
   */
  private AcademyVideoResponse persist(
      Academy academy, String originalFilename, TranscodeResult result) throws IOException {
    Long ownerUserId = academy.getTutor().getUserId();
    String storageKey = null;
    String thumbnailKey = null;
    try {
      try (InputStream mp4 = Files.newInputStream(result.mp4())) {
        storageKey = fileStorage.saveRaw(
            ownerUserId, "academy-video.mp4", mp4, result.sizeBytes());
      }
      thumbnailKey = fileStorage.saveImage(
          ownerUserId, "academy-video-thumb", Files.readAllBytes(result.thumbnail()));

      AcademyVideo saved = academyVideoRepository.save(AcademyVideo.builder()
          .academy(academy)
          .originalFilename(originalFilename)
          .storageKey(storageKey)
          .thumbnailKey(thumbnailKey)
          .durationSec(result.durationSec())
          .sizeBytes(result.sizeBytes())
          .width(result.width())
          .height(result.height())
          .status(VideoStatus.READY)
          .build());
      return AcademyVideoResponse.of(saved, academy.getSlug());
    } catch (RuntimeException | IOException e) {
      deleteQuietly(storageKey, VIDEO_FILE_TYPE);
      deleteQuietly(thumbnailKey, IMAGE_FILE_TYPE);
      throw e;
    }
  }

  /** 강사 대시보드 목록. 실패 건도 상태와 함께 보여준다. */
  public List<AcademyVideoResponse> listMine(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = academyService.requireMyAcademy(principal);
    List<AcademyVideo> videos = academyVideoRepository
        .findByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
            academy.getAcademyId(), VideoLifecycleStatus.READY);
    Map<Long, VideoOptimizationStatus> optimizationByVideo = videos.isEmpty()
            ? Map.of()
            : optimizationJobRepository
                .findByVideo_VideoIdIn(videos.stream().map(AcademyVideo::getVideoId).toList())
                .stream()
                .collect(Collectors.toMap(job -> job.getVideo().getVideoId(),
                    job -> job.getStatus(), (left, right) -> left));
    return videos.stream()
        .map(video -> AcademyVideoResponse.of(
            video, academy.getSlug(), optimizationByVideo.get(video.getVideoId())))
        .toList();
  }

  /** 공개 페이지 노출용. READY만, 최신순 최대 10개. */
  public List<AcademyVideoResponse> listPublicReady(String slug) {
    Academy academy = academyService.getPublishedEntity(slug);
    return listPublicReady(academy);
  }

  public List<AcademyVideoResponse> listPublicReady(Academy academy) {
    return academyVideoRepository
        .findTop10ByAcademy_AcademyIdAndStatusOrderByCreatedAtDesc(
            academy.getAcademyId(), VideoStatus.READY)
        .stream()
        .map(video -> AcademyVideoResponse.of(video, academy.getSlug()))
        .toList();
  }

  /**
   * 삭제 요청은 video row를 잠그고 DELETING tombstone + durable cleanup outbox를 원자적으로
   * 기록한다. 공개/강사 목록에서는 즉시 사라지지만 실제 object/job/session/video 삭제는 cleanup
   * worker가 부재를 확인한 뒤 FK-safe 순서로 완료한다.
   *
   * <p>수업상품이 대표 미디어 또는 OT로 쓰는 영상은 tombstone하지 않고 400으로 막는다. 참조
   * 검사와 tombstone은 같은 트랜잭션의 잠금 아래 직렬화되어 stale publication과 새 참조를 차단한다.
   */
  @Transactional
  public void delete(JwtTokenProvider.AuthenticatedPrincipal principal, Long videoId) {
    Academy academy = academyService.requireMyAcademy(principal);
    AcademyVideo video = academyVideoRepository
        .findForUpdateByVideoIdAndAcademyId(videoId, academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

    List<String> homepagePlacements = homepagePlacementsUsing(academy, videoId);
    if (!homepagePlacements.isEmpty()) {
      throw badRequest("학원 홈페이지의 " + String.join("·", homepagePlacements)
          + "에서 사용 중입니다. 빌더에서 영상을 먼저 해제해 주세요.");
    }

    List<String> usedBy = lessonMediaReferenceGuard.lessonNamesUsingForUpdate(videoId);
    if (!usedBy.isEmpty()) {
      throw badRequest("수업상품 '" + String.join("', '", usedBy)
          + "'에서 영상으로 사용 중입니다. 대표 미디어 또는 OT 영상을 먼저 해제해 주세요.");
    }

    cleanupTransactions.tombstoneLocked(video);
  }

  private List<String> homepagePlacementsUsing(Academy academy, Long videoId) {
    if (academy.getPageSettings() == null || academy.getPageSettings().builderDraft() == null) {
      return List.of();
    }
    Map<String, String> draft = academy.getPageSettings().builderDraft();
    String id = String.valueOf(videoId);
    return java.util.stream.Stream.of(
            Map.entry("heroVideoId", "히어로 영상"),
            Map.entry("principalVideoId", "원장 소개 영상"))
        .filter(entry -> id.equals(draft.get(entry.getKey())))
        .map(Map.Entry::getValue)
        .toList();
  }

  public byte[] readPublicThumbnail(String slug, Long videoId) {
    Academy academy = academyService.getPublishedEntity(slug);
    AcademyVideo found = academyVideoRepository
        .findByVideoIdAndAcademy_AcademyId(videoId, academy.getAcademyId())
        .filter(v -> v.getStatus() == VideoStatus.READY)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));

    try {
      return fileStorage.readPage(found.getThumbnailKey(), IMAGE_FILE_TYPE, 1);
    } catch (IOException e) {
      throw serverError("영상 썸네일을 읽을 수 없습니다.", e);
    }
  }

  private void validateProbe(VideoProbe probe) {
    if (probe.durationSec() > maxDurationSec) {
      throw badRequest("영상 길이는 " + maxDurationSec + "초 이하여야 합니다.");
    }
    if (!SUPPORTED_INPUT_CODECS.contains(probe.videoCodec().toLowerCase(Locale.ROOT))) {
      throw badRequest("지원하지 않는 영상 형식입니다: " + probe.videoCodec());
    }
  }

  private void deleteQuietly(String storageKey, String fileType) {
    if (storageKey != null) {
      fileStorage.delete(storageKey, fileType);
    }
  }

  private void deleteRecursively(Path directory) {
    try (var paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          log.warn("임시 파일 삭제 실패: {}", path, e);
        }
      });
    } catch (IOException e) {
      log.warn("임시 디렉터리 정리 실패: {}", directory, e);
    }
  }

  /** ffmpeg가 컨테이너 형식을 추론할 수 있게 확장자를 남긴다. 경로 조작 방지를 위해 영숫자만 허용. */
  private static String extensionOf(String originalFilename) {
    if (originalFilename == null) {
      return "";
    }
    int dot = originalFilename.lastIndexOf('.');
    if (dot < 0 || dot == originalFilename.length() - 1) {
      return "";
    }
    String extension = originalFilename.substring(dot + 1);
    return extension.matches("[A-Za-z0-9]{1,5}") ? "." + extension.toLowerCase(Locale.ROOT) : "";
  }

  private static long toMegabytes(long bytes) {
    return bytes / (1024 * 1024);
  }

  /** 사유가 그대로 사용자에게 보여야 하므로 전용 예외를 쓴다({@link VideoUploadRejectedException} 참고). */
  private static VideoUploadRejectedException badRequest(String message) {
    return new VideoUploadRejectedException(message);
  }

  private static ResponseStatusException serverError(String message, Throwable cause) {
    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
  }
}
