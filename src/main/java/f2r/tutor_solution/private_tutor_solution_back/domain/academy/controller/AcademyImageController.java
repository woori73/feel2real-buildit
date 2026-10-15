package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyImageDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 학원 홈페이지 이미지 관리 API.
 *
 * <p>PhotoCraft 기반 이미지 편집 (밝기·대비·노출·채도).
 * HeroMediaSlot(원장사진·교재표지·시간표)과 구분되는 홈페이지용 이미지 자산.
 *
 * <p>권한: TUTOR (학원별 격리).
 */
@RestController
@RequestMapping("/api/tutor/academy/images")
@RequiredArgsConstructor
public class AcademyImageController {

  private final AcademyImageService imageService;

  /**
   * 이미지 편집 (PhotoCraft).
   *
   * <p>편집 결과는 새 버전으로 저장되며, applyToPreview=true이면
   * 미리보기에 스테이징된다. 발행은 별도 승인 후 진행.
   */
  @PostMapping("/edit")
  public ResponseEntity<AcademyImageDtos.EditImageResponse> editImage(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody AcademyImageDtos.EditImageRequest request) {
    return ResponseEntity.ok(imageService.editImage(principal, request));
  }

  /**
   * 이미지 목록 조회 (학원별).
   */
  @GetMapping
  public ResponseEntity<List<AcademyImageDtos.ImageAssetInfo>> listImages(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(imageService.listImages(principal));
  }

  /**
   * 이전 버전으로 되돌리기.
   */
  @PostMapping("/{assetId}/revert")
  public ResponseEntity<AcademyImageDtos.ImageAssetInfo> revertImage(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long assetId) {
    return ResponseEntity.ok(imageService.revertImage(principal, assetId));
  }
}
