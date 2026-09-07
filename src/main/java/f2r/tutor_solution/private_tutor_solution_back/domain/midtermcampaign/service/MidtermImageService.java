package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 캠페인 팝업 비주얼·자료 썸네일·Making-of 이미지의 저장/삭제/조회.
 *
 * <p>기존 이미지 파이프라인({@link ImageSanitizer} 정규화 + {@link FileStorage})을 재사용한다.
 * storage key 는 캠페인 엔티티에만 저장하고 응답 payload 에는 싣지 않는다(전용 serve 경로로만).
 *
 * <p><b>보안 계약:</b> 클라이언트는 storage key 를 지정할 수 없다. {@link #save}가 만든 key 만
 * 엔티티에 붙고, {@link #read}는 넘어온 key 가 <b>그 소유자(userId)의 storage namespace</b> 안에
 * 있는지 재확인한 뒤에만 바이트를 읽는다({@link LocalFileStorage}가 {@code Path.of(key)}를 그대로
 * 쓰므로 raw key 가 들어오면 임의 로컬 경로/타 owner 파일 노출로 이어진다).
 */
@Service
public class MidtermImageService {

  private static final String IMAGE_FILE_TYPE = "IMAGE";
  private static final long MAX_SERVE_BYTES = 8L * 1024 * 1024;

  private final ImageSanitizer imageSanitizer;
  private final FileStorage fileStorage;
  private final UploadSizeGuard uploadSizeGuard;
  private final String localUploadDir;

  public MidtermImageService(ImageSanitizer imageSanitizer, FileStorage fileStorage,
      UploadSizeGuard uploadSizeGuard,
      @Value("${app.file.upload-dir:}") String localUploadDir) {
    this.imageSanitizer = imageSanitizer;
    this.fileStorage = fileStorage;
    this.uploadSizeGuard = uploadSizeGuard;
    this.localUploadDir = localUploadDir;
  }

  /**
   * 정규화 후 저장하고 storage key 를 돌려준다. {@code ownerUserId}는 소유 학원의 tutor userId 여야
   * 한다(호출부에서 {@code requireOwnedAcademyByHandle} 로 검증). key 는 항상 이 userId 의
   * namespace 안에 만들어진다.
   */
  public String save(Long ownerUserId, String filenameHint, MultipartFile image) {
    if (ownerUserId == null) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "소유자 정보가 없습니다.");
    }
    if (image == null || image.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 이미지가 없습니다.");
    }
    uploadSizeGuard.requireWithinDocumentLimit(image);
    try {
      ImageSanitizer.SanitizedImage normalized = imageSanitizer.sanitize(image);
      return fileStorage.saveImage(ownerUserId, filenameHint, normalized.bytes(),
          normalized.extension());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장에 실패했습니다.", e);
    }
  }

  /** 파일 삭제(best-effort). 새 이미지 교체가 성공한 뒤에만 호출한다(§5). */
  public void delete(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      return;
    }
    fileStorage.delete(storageKey, IMAGE_FILE_TYPE);
  }

  public record ServedImage(byte[] bytes, String contentType) {}

  /**
   * storage key 의 바이트를 읽는다. 넘어온 key 는 캠페인 엔티티에 저장돼 있던 값이어야 하고,
   * 추가로 {@code ownerUserId} 의 이미지 namespace 안에 있는지 재확인한다.
   *
   * @throws ResponseStatusException 404 — key 부재 / traversal / 소유 namespace 밖 / 파일 없음
   */
  public ServedImage read(String storageKey, Long ownerUserId) {
    if (storageKey == null || storageKey.isBlank() || ownerUserId == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지가 없습니다.");
    }
    assertOwnedImageKey(storageKey, ownerUserId);
    try {
      byte[] bytes = fileStorage.readSingleLimited(storageKey, IMAGE_FILE_TYPE, MAX_SERVE_BYTES);
      return new ServedImage(bytes, ImageSanitizer.contentTypeForStorageKey(storageKey));
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.", e);
    }
  }

  /**
   * key 가 {@code ownerUserId} 의 이미지 저장 namespace 안에 있는지 검증한다.
   *
   * <ul>
   *   <li>path traversal({@code ..}) 절대 불가
   *   <li>Local: 정규화된 절대경로가 {@code {upload-dir}/{ownerUserId}/} 아래여야 한다
   *       → 임의 로컬 경로({@code /etc/passwd})·타 owner 디렉터리 차단
   *   <li>S3: 객체 키가 {@code .../users/{ownerUserId}/<파일>} 형태여야 한다(하위 경로 없음)
   * </ul>
   */
  private static final java.util.regex.Pattern S3_IMAGE_KEY =
      java.util.regex.Pattern.compile("(?:.*/)?users/(\\d+)/[^/]+");

  private void assertOwnedImageKey(String storageKey, Long ownerUserId) {
    String key = storageKey.replace('\\', '/');
    if (key.contains("..") || key.isBlank()) {
      throw notOwned();
    }
    String owner = String.valueOf(ownerUserId);

    // S3 객체 키: "{prefix}/users/{userId}/{uuid}_{name}.{ext}" (하위 경로 없음).
    if (!key.startsWith("/") && !key.matches("(?i)^[a-z]:/.*")) {
      java.util.regex.Matcher m = S3_IMAGE_KEY.matcher(key);
      if (m.matches()) {
        if (m.group(1).equals(owner)) {
          return;
        }
        throw notOwned();
      }
    }

    // Local 저장: 정규화된 경로가 {upload-dir}/{ownerUserId}/ 아래여야 한다.
    if (localUploadDir == null || localUploadDir.isBlank()) {
      throw notOwned();
    }
    java.nio.file.Path normalized = java.nio.file.Path.of(storageKey).normalize();
    java.nio.file.Path ownerDir = java.nio.file.Path.of(localUploadDir, owner).normalize();
    if (!normalized.startsWith(ownerDir) || normalized.equals(ownerDir)) {
      throw notOwned();
    }
  }

  private static ResponseStatusException notOwned() {
    // 경로/원인을 노출하지 않는다.
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.");
  }
}
