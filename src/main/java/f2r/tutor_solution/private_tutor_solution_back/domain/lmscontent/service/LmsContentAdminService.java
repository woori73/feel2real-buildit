package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.AdminContentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.AdminContentsView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.ContentDetailsUpsert;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.LessonOption;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContent;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContentType;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.repository.LmsContentRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.TranscodeResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoProbe;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 강사용 LMS 콘텐츠 관리(작업지시서 §2·§4, {@code /lms/{handle}/contents}).
 *
 * <p><b>클래스 불변식:</b> 모든 public 메서드는 {@link AcademyService#requireOwnedAcademyByHandle}로
 * 시작한다({@code MidtermCampaignAdminService}와 같은 규칙).
 *
 * <p>업로드는 {@code AcademyVideoService.upload()}와 같은 방식(요청 스레드 동기 처리, ffprobe 검증 →
 * ffmpeg 정규화 → 저장 → row insert, 실패 시 이미 올라간 산출물 보상 삭제)이지만 <b>정책은 완전히
 * 분리</b>한다 — 길이 상한은 홍보영상의 120초가 아니라 {@code app.lms.content.max-duration-sec}
 * (기본 30분)이다. {@link VideoTranscoder}(ffmpeg/ffprobe)와 {@link FileStorage}(저장)만 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LmsContentAdminService {

  private static final Set<String> SUPPORTED_INPUT_CODECS =
      Set.of("h264", "hevc", "vp8", "vp9", "av1", "mpeg4");

  private final AcademyService academyService;
  private final LessonProductRepository lessonProductRepository;
  private final LmsContentRepository lmsContentRepository;
  private final FileStorage fileStorage;
  private final VideoTranscoder videoTranscoder;

  @Value("${app.lms.content.max-duration-sec:1800}")
  private int maxDurationSec;
  @Value("${app.lms.content.max-upload-bytes:1073741824}")
  private long maxUploadBytes;
  @Value("${app.lms.content.max-output-bytes:536870912}")
  private long maxOutputBytes;

  @Transactional(readOnly = true)
  public AdminContentsView list(String handle) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    List<LessonOption> lessons = lessonProductRepository
        .findByAcademy_AcademyIdOrderByCreatedAtAscLessonIdAsc(academy.getAcademyId())
        .stream()
        .map(l -> new LessonOption(l.getLessonId(), l.getName(), l.getSchoolName(), l.getGradeLabel()))
        .toList();
    List<AdminContentView> contents = lmsContentRepository
        .findByAcademy_AcademyIdOrderByCreatedAtDesc(academy.getAcademyId())
        .stream()
        .map(LmsContentAdminService::toView)
        .toList();
    return new AdminContentsView(lessons, contents);
  }

  /** 강좌 선택 + 메타데이터 + 영상 업로드를 한 번의 저장으로 처리한다(작업지시서 §4). */
  public AdminContentView createWithVideo(
      String handle, ContentDetailsUpsert details, MultipartFile video) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    LessonProduct lesson = requireOwnedLesson(academy, details.lessonId());
    validateDetails(details);

    if (video == null || video.isEmpty()) {
      throw badRequest("업로드할 영상 파일이 없습니다.");
    }
    if (video.getSize() > maxUploadBytes) {
      throw badRequest("영상 용량은 " + toMegabytes(maxUploadBytes) + "MB를 넘을 수 없습니다.");
    }

    Path workDir = createWorkDir();
    try {
      Path input = workDir.resolve("input" + extensionOf(video.getOriginalFilename()));
      video.transferTo(input);

      VideoProbe probe = videoTranscoder.probe(input);
      validateProbe(probe);

      TranscodeResult result = videoTranscoder.transcode(input, probe, workDir);
      if (result.sizeBytes() > maxOutputBytes) {
        throw badRequest("변환된 영상이 너무 큽니다. 더 짧거나 작은 영상을 올려 주세요.");
      }
      return persist(academy, lesson, details, video.getOriginalFilename(), result);
    } catch (IOException e) {
      throw serverError("영상 업로드 처리에 실패했습니다.", e);
    } finally {
      deleteRecursively(workDir);
    }
  }

  @Transactional
  public AdminContentView updateDetails(String handle, Long lmsContentId, ContentDetailsUpsert details) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    LmsContent content = requireOwnedContent(academy, lmsContentId);
    validateDetails(details);
    // 강좌(lessonId) 재지정은 이번 화면 범위 밖이다 — 콘텐츠는 처음 저장한 강좌에 계속 속한다.
    content.updateDetails(
        details.title(), details.description(), details.schoolName(), details.gradeLabel(),
        details.examName(), details.unitLabel(),
        details.displayOrder() == null ? content.getDisplayOrder() : details.displayOrder());
    return toView(content);
  }

  @Transactional
  public AdminContentView setPublished(String handle, Long lmsContentId, boolean published) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    LmsContent content = requireOwnedContent(academy, lmsContentId);
    if (published) {
      content.publish(LocalDateTime.now());
    } else {
      content.hide();
    }
    return toView(content);
  }

  @Transactional
  public void delete(String handle, Long lmsContentId) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    LmsContent content = requireOwnedContent(academy, lmsContentId);
    String storageKey = content.getStorageKey();
    String thumbnailKey = content.getThumbnailKey();
    lmsContentRepository.delete(content);
    deleteQuietly(storageKey, "VIDEO");
    deleteQuietly(thumbnailKey, "IMAGE");
  }

  private AdminContentView persist(
      Academy academy, LessonProduct lesson, ContentDetailsUpsert details,
      String originalFilename, TranscodeResult result) throws IOException {
    Long ownerUserId = academy.getTutor().getUserId();
    String storageKey = null;
    String thumbnailKey = null;
    try {
      try (InputStream mp4 = Files.newInputStream(result.mp4())) {
        String rawStorageKey = fileStorage.saveRaw(ownerUserId, "lms-content.mp4", mp4, result.sizeBytes());
        // Convert to absolute path for LocalAcademyVideoDeliveryStorage compatibility
        storageKey = Path.of(rawStorageKey).toAbsolutePath().toString();
      }
      thumbnailKey = fileStorage.saveImage(
          ownerUserId, "lms-content-thumb", Files.readAllBytes(result.thumbnail()));
      // Convert thumbnail key to absolute path for consistency
      thumbnailKey = Path.of(thumbnailKey).toAbsolutePath().toString();

      LmsContent saved = lmsContentRepository.save(LmsContent.builder()
          .academy(academy)
          .lessonProduct(lesson)
          .title(details.title())
          .description(details.description())
          .contentType(LmsContentType.VIDEO)
          .originalFilename(originalFilename)
          .storageKey(storageKey)
          .thumbnailKey(thumbnailKey)
          .durationSec(result.durationSec())
          .sizeBytes(result.sizeBytes())
          .width(result.width())
          .height(result.height())
          .schoolName(details.schoolName())
          .gradeLabel(details.gradeLabel())
          .examName(details.examName())
          .unitLabel(details.unitLabel())
          .displayOrder(details.displayOrder() == null ? 0 : details.displayOrder())
          .status(LmsContentStatus.READY)
          .build());
      return toView(saved);
    } catch (RuntimeException | IOException e) {
      deleteQuietly(storageKey, "VIDEO");
      deleteQuietly(thumbnailKey, "IMAGE");
      throw e;
    }
  }

  private LessonProduct requireOwnedLesson(Academy academy, Long lessonId) {
    if (lessonId == null) {
      throw badRequest("강좌를 선택해 주세요.");
    }
    return lessonProductRepository.findByLessonIdAndAcademy_AcademyId(lessonId, academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강좌를 찾을 수 없습니다."));
  }

  private LmsContent requireOwnedContent(Academy academy, Long lmsContentId) {
    return lmsContentRepository
        .findByLmsContentIdAndAcademy_AcademyId(lmsContentId, academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "콘텐츠를 찾을 수 없습니다."));
  }

  private static void validateDetails(ContentDetailsUpsert details) {
    if (details == null || details.title() == null || details.title().isBlank()) {
      throw badRequest("제목을 입력해 주세요.");
    }
    if (details.title().length() > 200) {
      throw badRequest("제목은 200자 이하여야 합니다.");
    }
  }

  private void validateProbe(VideoProbe probe) {
    if (probe.durationSec() > maxDurationSec) {
      throw badRequest("영상 길이는 " + (maxDurationSec / 60) + "분 이하여야 합니다.");
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

  private static Path createWorkDir() {
    try {
      return Files.createTempDirectory("lms-content-");
    } catch (IOException e) {
      throw serverError("영상 처리 작업 공간을 만들 수 없습니다.", e);
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

  private static String extensionOf(String originalFilename) {
    if (originalFilename == null) return "";
    int dot = originalFilename.lastIndexOf('.');
    if (dot < 0 || dot == originalFilename.length() - 1) return "";
    String extension = originalFilename.substring(dot + 1);
    return extension.matches("[A-Za-z0-9]{1,5}") ? "." + extension.toLowerCase(Locale.ROOT) : "";
  }

  private static long toMegabytes(long bytes) {
    return bytes / (1024 * 1024);
  }

  static AdminContentView toView(LmsContent content) {
    return new AdminContentView(
        content.getLmsContentId(),
        content.getLessonProduct().getLessonId(),
        content.getLessonProduct().getName(),
        content.getTitle(),
        content.getDescription(),
        content.getStatus().name(),
        content.getSchoolName(),
        content.getGradeLabel(),
        content.getExamName(),
        content.getUnitLabel(),
        content.getDisplayOrder(),
        content.getStorageKey() != null,
        content.getDurationSec(),
        content.getSizeBytes(),
        content.getPublishedAt(),
        content.getCreatedAt());
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException serverError(String message, Throwable cause) {
    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
  }
}
