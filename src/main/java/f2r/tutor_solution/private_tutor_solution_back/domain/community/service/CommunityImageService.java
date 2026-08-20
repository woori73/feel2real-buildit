package f2r.tutor_solution.private_tutor_solution_back.domain.community.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.dto.CommunityImageResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityImage;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityImageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;

/** 커뮤니티 질문/답변 본문 이미지 업로드/서빙. academy 블로그 이미지 패턴 미러(tutor 전용이 아님). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityImageService {

  private final CommunityImageRepository imageRepository;
  private final UserRepository userRepository;
  private final ImageSanitizer imageSanitizer;
  private final FileStorage fileStorage;

  @Transactional
  public CommunityImageResponse upload(JwtTokenProvider.AuthenticatedPrincipal principal, MultipartFile image) {
    if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    if (image == null || image.isEmpty()) throw new IllegalArgumentException("이미지를 선택해 주세요.");
    User uploader = userRepository.findById(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    try {
      AuditorContextHolder.set(principal.loginId());
      ImageSanitizer.SanitizedImage sanitized = imageSanitizer.sanitize(image);
      String storageKey = fileStorage.saveImage(
          uploader.getUserId(), image.getOriginalFilename(), sanitized.bytes(), sanitized.extension());
      CommunityImage saved = imageRepository.save(CommunityImage.builder()
          .uploadedBy(uploader).storageKey(storageKey).contentType(sanitized.contentType())
          .fileSize((long) sanitized.bytes().length).build());
      return new CommunityImageResponse(saved.getImageId(), "/api/open-community/images/" + saved.getImageId());
    } catch (IOException e) {
      throw new UncheckedIOException("커뮤니티 이미지 업로드 실패", e);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /**
   * 서빙용 이미지 bytes + Content-Type.
   *
   * <p>Content-Type을 함께 돌려주는 이유: 애니메이션 GIF는 {@code image/gif}로 내려가야 움직인다.
   * 고정 {@code image/jpeg}로 내리면 브라우저가 첫 프레임만 그리거나 렌더링을 거부한다.
   */
  public record ServedImage(byte[] bytes, String contentType) {}

  public ServedImage readImage(Long imageId) {
    CommunityImage img = imageRepository.findById(imageId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."));
    try {
      byte[] bytes = fileStorage.readPage(img.getStorageKey(), "IMAGE", 1);
      // 레거시 row는 contentType이 image/jpeg로 채워져 있고 실제로도 JPEG다 (backfill 불필요).
      String contentType = img.getContentType() != null && !img.getContentType().isBlank()
          ? img.getContentType()
          : ImageSanitizer.contentTypeForStorageKey(img.getStorageKey());
      return new ServedImage(bytes, contentType);
    } catch (IOException e) {
      throw new UncheckedIOException("커뮤니티 이미지 조회 실패", e);
    }
  }
}
