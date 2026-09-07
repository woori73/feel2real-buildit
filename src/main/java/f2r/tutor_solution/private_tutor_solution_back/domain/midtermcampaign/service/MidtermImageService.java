package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 캠페인 팝업 비주얼·자료 썸네일 이미지의 저장/조회. 기존 이미지 파이프라인
 * ({@link ImageSanitizer} 정규화 + {@link FileStorage})을 재사용한다. storage key 는 캠페인
 * 엔티티에만 저장하고 응답 payload 에는 싣지 않는다(전용 serve 경로로만 노출).
 */
@Service
@RequiredArgsConstructor
public class MidtermImageService {

  private static final String IMAGE_FILE_TYPE = "IMAGE";
  private static final long MAX_SERVE_BYTES = 8L * 1024 * 1024;

  private final ImageSanitizer imageSanitizer;
  private final FileStorage fileStorage;
  private final UploadSizeGuard uploadSizeGuard;

  /** 정규화 후 저장하고 storage key 를 돌려준다. 소유자 검증은 호출부(admin 서비스)가 이미 했다. */
  public String save(Long ownerUserId, String filenameHint, MultipartFile image) {
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

  public record ServedImage(byte[] bytes, String contentType) {}

  /** storage key 의 바이트를 읽는다. key 는 호출부가 캠페인 엔티티에서 꺼내 검증한 값이어야 한다. */
  public ServedImage read(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지가 없습니다.");
    }
    try {
      byte[] bytes = fileStorage.readSingleLimited(storageKey, IMAGE_FILE_TYPE, MAX_SERVE_BYTES);
      return new ServedImage(bytes, ImageSanitizer.contentTypeForStorageKey(storageKey));
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.", e);
    }
  }
}
