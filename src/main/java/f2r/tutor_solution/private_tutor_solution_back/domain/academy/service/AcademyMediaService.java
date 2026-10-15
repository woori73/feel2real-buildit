package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.HeroMediaSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Semaphore;

/**
 * 수업상품 대표 이미지의 업로드·조회.
 *
 * <p>이미지 정규화는 {@link ImageSanitizer}를 그대로 쓴다 — 최장변 1600px, EXIF 보정, 품질 0.82
 * JPEG. 학원 로고/커버, 블로그, 커뮤니티가 이미 쓰는 그 경로다.
 *
 * <p>단 iPhone 기본 포맷인 HEIC는 stock ImageIO에 리더가 없다. 그 경우에만 ffmpeg로 PNG를 거쳐
 * 다시 sanitizer에 넣는다 — 리사이즈/EXIF/품질 로직이 한 곳에만 있게 하기 위해서다.
 *
 * <p>자원 방어가 두 겹인 이유: 바이트 상한({@link UploadSizeGuard})만으로는 힙을 묶을 수 없다.
 * 압축률이 높은 포맷은 20MB 파일이 수천만 픽셀로 펴지고, 디코딩된 버퍼는 픽셀당 4바이트를 먹는다.
 * 그래서 픽셀 수 상한을 따로 둔다.
 */
@Slf4j
@Service
public class AcademyMediaService {

  private static final String IMAGE_FILE_TYPE = "IMAGE";
  private static final String LESSON_HERO_FILENAME_HINT = "lesson-hero";

  private final AcademyService academyService;
  private final AcademyWorkingCopyService workingCopyService;
  private final LessonProductRepository lessonProductRepository;
  private final ImageSanitizer imageSanitizer;
  private final FileStorage fileStorage;
  private final UploadSizeGuard uploadSizeGuard;
  private final VideoTranscoder videoTranscoder;

  private final long maxDecodedPixels;
  private final Semaphore decodePermits;

  public AcademyMediaService(
      AcademyService academyService,
      AcademyWorkingCopyService workingCopyService,
      LessonProductRepository lessonProductRepository,
      ImageSanitizer imageSanitizer,
      FileStorage fileStorage,
      UploadSizeGuard uploadSizeGuard,
      VideoTranscoder videoTranscoder,
      @Value("${app.image.max-decoded-pixels:40000000}") long maxDecodedPixels,
      @Value("${app.image.max-concurrent-decodes:2}") int maxConcurrentDecodes) {
    this.academyService = academyService;
    this.workingCopyService = workingCopyService;
    this.lessonProductRepository = lessonProductRepository;
    this.imageSanitizer = imageSanitizer;
    this.fileStorage = fileStorage;
    this.uploadSizeGuard = uploadSizeGuard;
    this.videoTranscoder = videoTranscoder;
    this.maxDecodedPixels = maxDecodedPixels;
    this.decodePermits = new Semaphore(maxConcurrentDecodes);
  }

  /**
   * 대표 이미지를 정규화해 저장하고 storage key를 돌려준다.
   *
   * <p>{@code lessonId}를 받지 않는다. 편집기에서 새로 만든 수업상품은 컬렉션 저장 전까지 id가 없다
   * — 여기서 id를 요구하면 "먼저 저장하세요"라는 동선이 강제된다. 소유는 학원 단위로 판정하고,
   * 수업상품과의 연결은 컬렉션 저장이 맡는다.
   */
  public String uploadLessonHeroImage(
      JwtTokenProvider.AuthenticatedPrincipal principal, MultipartFile image) {
    Academy academy = academyService.requireMyAcademy(principal);

    if (image == null || image.isEmpty()) {
      throw badRequest("업로드할 이미지가 없습니다.");
    }
    // 컨테이너 multipart 한도는 영상(100MB) 때문에 열려 있다. 이미지 경로는 여기서 따로 막아야
    // 전역 한도까지 통과하지 않는다.
    uploadSizeGuard.requireWithinDocumentLimit(image);

    if (!decodePermits.tryAcquire()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "이미지 처리 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    }
    try {
      ImageSanitizer.SanitizedImage normalized = normalize(image);
      return fileStorage.saveImage(
          academy.getTutor().getUserId(), LESSON_HERO_FILENAME_HINT,
          normalized.bytes(), normalized.extension());
    } catch (IOException e) {
      throw serverError("이미지 저장에 실패했습니다.", e);
    } finally {
      decodePermits.release();
    }
  }

  /**
   * 히어로 이미지 슬롯(원장사진/교재표지)을 정규화해 저장하고 Academy에 연결한다.
   *
   * <p>수업상품 대표 이미지와 달리 대상이 Academy 하나로 고정이라, key만 돌려주지 않고
   * 여기서 바로 슬롯에 반영한다(별도 저장 단계가 없다).
   */
  public void uploadHeroMedia(
      JwtTokenProvider.AuthenticatedPrincipal principal, HeroMediaSlot slot, MultipartFile image) {
    academyService.updateHeroMedia(principal, slot, storeHeroImage(principal, slot, image));
  }

  /**
   * 이미지를 검증·정규화해 저장소에 올리고 storage key만 돌려준다. <b>DB(공개·작업본)는 바꾸지 않는다</b> —
   * 호출자가 즉시 반영({@link #uploadHeroMedia})할지, 작업본에 예약({@code stageMedia})할지 정한다.
   */
  public String storeHeroImage(
      JwtTokenProvider.AuthenticatedPrincipal principal, HeroMediaSlot slot, MultipartFile image) {
    Academy academy = academyService.requireMyAcademy(principal);

    if (image == null || image.isEmpty()) {
      throw badRequest("업로드할 이미지가 없습니다.");
    }
    uploadSizeGuard.requireWithinDocumentLimit(image);

    if (!decodePermits.tryAcquire()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "이미지 처리 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    }
    try {
      ImageSanitizer.SanitizedImage normalized = normalize(image);
      return fileStorage.saveImage(
          academy.getTutor().getUserId(), slot.filenameHint(),
          normalized.bytes(), normalized.extension());
    } catch (IOException e) {
      throw serverError("이미지 저장에 실패했습니다.", e);
    } finally {
      decodePermits.release();
    }
  }

  /** 히어로 이미지 슬롯 비우기. 시안 placeholder로 되돌아간다. */
  public void deleteHeroMedia(
      JwtTokenProvider.AuthenticatedPrincipal principal, HeroMediaSlot slot) {
    academyService.updateHeroMedia(principal, slot, null);
  }

  /** 공개 페이지용 히어로 이미지 바이트. 발행된 학원만 통과한다. */
  public ServedImage readPublicHeroMedia(String slug, HeroMediaSlot slot) {
    return readHeroMedia(academyService.getPublishedEntity(slug), slot);
  }

  /** 빌더 프리뷰용 히어로 이미지 바이트. 미발행(DRAFT) 학원도 렌더해야 하므로 소유자 경로를 따로 둔다. */
  public ServedImage readMyHeroMedia(
      JwtTokenProvider.AuthenticatedPrincipal principal, HeroMediaSlot slot) {
    return readHeroMedia(academyService.requireMyAcademy(principal), slot);
  }

  /**
   * 작업본 미리보기용 이미지. 이 슬롯에 예약(교체)이 있으면 그 파일, 비우기 예약이면 없음, 예약이 없으면 공개 슬롯 그대로.
   * 소유자만 읽을 수 있고 storage key는 밖으로 나가지 않는다.
   */
  public ServedImage readWorkingCopyMedia(
      JwtTokenProvider.AuthenticatedPrincipal principal, HeroMediaSlot slot) {
    java.util.Map<String, String> staged = workingCopyService.stagedMedia(principal);
    if (staged.containsKey(slot.key())) {
      String key = staged.get(slot.key());
      if (key == null || key.isEmpty()) {
        throw notFound("등록된 이미지가 없습니다.");
      }
      try {
        return served(key, fileStorage.readPage(key, IMAGE_FILE_TYPE, 1));
      } catch (IOException e) {
        throw serverError("이미지를 읽을 수 없습니다.", e);
      }
    }
    return readMyHeroMedia(principal, slot);
  }

  private ServedImage readHeroMedia(Academy academy, HeroMediaSlot slot) {
    String key = AcademyService.heroMediaKey(academy, slot);
    if (key == null) {
      throw notFound("등록된 이미지가 없습니다.");
    }
    try {
      return served(key, fileStorage.readPage(key, IMAGE_FILE_TYPE, 1));
    } catch (IOException e) {
      throw serverError("이미지를 읽을 수 없습니다.", e);
    }
  }

  /**
   * 서빙용 이미지 bytes + Content-Type.
   *
   * <p>애니메이션 GIF는 {@code image/gif}로 내려가야 움직인다. Content-Type 컬럼이 없으므로 저장 키의
   * 확장자에서 되찾는다 — 확장자를 붙이는 쪽({@link ImageSanitizer.SanitizedImage})과 같은 규칙이다.
   */
  public record ServedImage(byte[] bytes, String contentType) {}

  private static ServedImage served(String storageKey, byte[] bytes) {
    return new ServedImage(bytes, ImageSanitizer.contentTypeForStorageKey(storageKey));
  }

  /**
   * 공개 페이지용 대표 이미지 바이트.
   *
   * <p>storage key를 응답 DTO로 내보내지 않으므로 이 경로가 유일한 공개 노출 지점이다.
   * 발행된 학원의 수업상품만 통과한다.
   */
  public ServedImage readPublicHeroImage(String slug, Long lessonId) {
    Academy academy = academyService.getPublishedEntity(slug);
    LessonProduct lesson = lessonProductRepository
        .findByLessonIdAndAcademy_AcademyId(lessonId, academy.getAcademyId())
        .orElseThrow(() -> notFound("수업상품을 찾을 수 없습니다."));
    return readHeroImage(lesson);
  }

  /**
   * 강사 빌더 프리뷰용 대표 이미지 바이트.
   *
   * <p>빌더는 아직 발행되지 않은(DRAFT) 학원도 렌더한다. 공개 경로는 발행 상태를 요구해서 404가
   * 나므로 소유자 전용 경로를 따로 둔다.
   */
  public ServedImage readMyHeroImage(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long lessonId) {
    Academy academy = academyService.requireMyAcademy(principal);
    LessonProduct lesson = lessonProductRepository
        .findByLessonIdAndAcademy_AcademyId(lessonId, academy.getAcademyId())
        .orElseThrow(() -> notFound("수업상품을 찾을 수 없습니다."));
    return readHeroImage(lesson);
  }

  private ServedImage readHeroImage(LessonProduct lesson) {
    String key = lesson.getMediaImageKey();
    if (key == null) {
      throw notFound("대표 이미지가 없습니다.");
    }
    try {
      return served(key, fileStorage.readPage(key, IMAGE_FILE_TYPE, 1));
    } catch (IOException e) {
      throw serverError("대표 이미지를 읽을 수 없습니다.", e);
    }
  }

  /** ImageIO가 읽는 포맷은 바로, 아니면(HEIC 등) ffmpeg를 한 번 거쳐 정규화한다. */
  private ImageSanitizer.SanitizedImage normalize(MultipartFile image) throws IOException {
    byte[] original = image.getBytes();

    if (imageSanitizer.canDecode(original)) {
      requireWithinPixelLimit(imageSanitizer.readDimension(original));
      // 이미 읽어 둔 바이트를 넘긴다. MultipartFile 오버로드를 쓰면 같은 파일을 두 번 읽는다.
      return imageSanitizer.sanitize(original, image.getOriginalFilename());
    }
    return normalizeViaFfmpeg(image, original);
  }

  /**
   * HEIC/AVIF처럼 ImageIO가 못 읽는 포맷 경로.
   *
   * <p>ffmpeg 단계에서 최장변을 미리 캡한다. 이 경로는 헤더 픽셀 판정이 불가능해서(ImageIO가 못
   * 읽으니 크기도 못 읽는다) 캡이 없으면 8000x6000짜리 폰 사진이 그대로 디코딩돼 수백 MB를 먹는다.
   * 최종 축소·EXIF·품질은 그대로 sanitizer가 처리하고, 여기 캡은 안전 천장일 뿐이다.
   */
  private ImageSanitizer.SanitizedImage normalizeViaFfmpeg(MultipartFile image, byte[] original)
      throws IOException {
    Path workDir = Files.createTempDirectory("lesson-hero-image-");
    try {
      Path input = workDir.resolve("input" + extensionOf(image.getOriginalFilename()));
      Files.write(input, original);
      Path decoded = workDir.resolve("decoded.png");

      videoTranscoder.decodeStillImage(input, decoded, ImageSanitizer.MAX_DIMENSION);

      byte[] png = Files.readAllBytes(decoded);
      if (!imageSanitizer.canDecode(png)) {
        throw badRequest("지원하지 않는 이미지 형식입니다.");
      }
      requireWithinPixelLimit(imageSanitizer.readDimension(png));
      // ffmpeg가 정지 PNG로 펴 놓은 상태라 이 경로의 결과는 항상 JPEG다.
      return imageSanitizer.sanitize(png, image.getOriginalFilename());
    } finally {
      deleteRecursively(workDir);
    }
  }

  private void requireWithinPixelLimit(ImageSanitizer.Dimension dimension) {
    if (dimension == null) {
      throw badRequest("이미지 크기를 확인할 수 없습니다.");
    }
    if (dimension.pixels() > maxDecodedPixels) {
      throw badRequest("이미지 해상도가 너무 큽니다. "
          + dimension.width() + "x" + dimension.height()
          + " (최대 " + maxDecodedPixels / 1_000_000 + "메가픽셀)");
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

  /** ffmpeg가 컨테이너 형식을 추론하도록 확장자를 남긴다. 경로 조작 방지를 위해 영숫자만 허용. */
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

  private static ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException serverError(String message, Throwable cause) {
    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
  }
}
