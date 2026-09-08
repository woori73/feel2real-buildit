package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.HeroMediaSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyMediaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoDeliveryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

/**
 * 학원 홍보 영상 API.
 *
 * <p>강사 경로는 {@code /api/tutor/**}(TUTOR role)로, 공개 경로는 {@code /api/academies/**}(permitAll)로
 * 기존 SecurityConfig 규칙에 그대로 얹힌다 — 신규 security rule이 필요 없다.
 */
@RestController
@RequiredArgsConstructor
public class AcademyVideoController {

  private final AcademyVideoService academyVideoService;
  private final AcademyVideoDeliveryService academyVideoDeliveryService;
  private final AcademyMediaService academyMediaService;

  /**
   * 수업상품 대표 이미지 업로드. lessonId를 받지 않는다 — 편집기에서 새로 만든 수업상품은 컬렉션
   * 저장 전까지 id가 없다. 반환된 key를 편집기가 들고 있다가 컬렉션 저장 때 함께 보낸다.
   */
  @PostMapping(
      value = "/api/tutor/academy/media/lesson-hero",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadLessonHeroImage(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("image") MultipartFile image) {
    return ResponseEntity.ok(
        Map.of("imageKey", academyMediaService.uploadLessonHeroImage(principal, image)));
  }

  /**
   * 학원 영상을 애플리케이션 서버로 multipart 업로드한다.
   *
   * <p>브라우저가 S3 presigned URL로 직접 PUT하지 않는다. 서버가 원본을 임시 디스크에 받은 뒤
   * 검증·MP4 정규화·썸네일 생성을 수행하고 private storage에 저장한다.
   */
  @PostMapping(value = "/api/tutor/academy/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AcademyVideoResponse> upload(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("video") MultipartFile video,
      @RequestParam(value = "purpose", required = false) String purpose) {
    return ResponseEntity.ok(academyVideoService.upload(principal, video, purpose));
  }

  /**
   * 외부에 이미 올라간 영상(네이버·YouTube 등)을 URL 로만 등록한다(작업지시서 §3). 파일을
   * 받지 않는 JSON 요청이며 서버로 다운로드/복제하지 않는다 — 개수 상한은 직접 업로드와 합산된다.
   */
  @PostMapping(
      value = "/api/tutor/academy/videos/link",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AcademyVideoResponse> registerExternalLink(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody(required = false) ExternalVideoLinkRequest request) {
    ExternalVideoLinkRequest req = request == null
        ? new ExternalVideoLinkRequest(null, null, null) : request;
    return ResponseEntity.ok(academyVideoService.registerExternalLink(
        principal, req.url(), req.title(), req.purpose()));
  }

  /**
   * @param url 외부 영상/콘텐츠 페이지 URL(https 만). @param title 목록에 표시할 제목.
   *            @param purpose {@code "LESSON"} | {@code "DIRECTOR"} | {@code "NONE"}.
   */
  public record ExternalVideoLinkRequest(String url, String title, String purpose) {}

  @GetMapping("/api/tutor/academy/videos")
  public ResponseEntity<List<AcademyVideoResponse>> listMine(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(academyVideoService.listMine(principal));
  }

  /**
   * 영상 용도(재)지정. {@code purpose IS NULL}("용도 미지정") 레거시 영상의 최초 분류와
   * DIRECTOR↔LESSON 재지정을 모두 이 하나의 엔드포인트로 처리한다.
   */
  @PatchMapping("/api/tutor/academy/videos/{videoId}/purpose")
  public ResponseEntity<AcademyVideoResponse> updatePurpose(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long videoId,
      @RequestBody VideoPurposeUpdateRequest request) {
    return ResponseEntity.ok(
        academyVideoService.updatePurpose(principal, videoId, request == null ? null : request.purpose()));
  }

  /** @param purpose {@code "DIRECTOR"} 또는 {@code "LESSON"}. 빈 값은 서비스 레이어에서 거부한다. */
  public record VideoPurposeUpdateRequest(String purpose) {}

  @DeleteMapping("/api/tutor/academy/videos/{videoId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long videoId) {
    academyVideoService.delete(principal, videoId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/academies/{slug}/videos")
  public ResponseEntity<List<AcademyVideoResponse>> listPublic(@PathVariable String slug) {
    return ResponseEntity.ok(academyVideoService.listPublicReady(slug));
  }

  @GetMapping("/api/academies/{slug}/videos/{videoId}/stream")
  public ResponseEntity<StreamingResponseBody> streamVideo(
      @PathVariable String slug,
      @PathVariable Long videoId,
      @RequestHeader(value = "Range", required = false) String range) {
    return academyVideoDeliveryService.get(slug, videoId, range);
  }

  @RequestMapping(
      value = "/api/academies/{slug}/videos/{videoId}/stream",
      method = RequestMethod.HEAD)
  public ResponseEntity<Void> headVideo(
      @PathVariable String slug, @PathVariable Long videoId) {
    return academyVideoDeliveryService.head(slug, videoId);
  }

  @GetMapping(
      value = "/api/academies/{slug}/videos/{videoId}/thumbnail",
      produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<byte[]> thumbnail(@PathVariable String slug, @PathVariable Long videoId) {
    // poster 는 videoId 당 불변이다 — 매 요청 재다운로드하면 "poster 먼저 표시"가 늦는다.
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate().immutable())
        .body(academyVideoService.readPublicThumbnail(slug, videoId));
  }

  /** 공개 수업상품 히어로 이미지. 응답 DTO에 storage key를 싣지 않으므로 이 경로로만 노출된다. */
  @GetMapping("/api/academies/{slug}/lessons/{lessonId}/hero-image")
  public ResponseEntity<byte[]> publicHeroImage(
      @PathVariable String slug, @PathVariable Long lessonId) {
    return imageResponse(academyMediaService.readPublicHeroImage(slug, lessonId));
  }

  /**
   * 강사 빌더 프리뷰용 히어로 이미지.
   *
   * <p>빌더는 미발행(DRAFT) 학원도 렌더하는데 공개 경로는 발행 상태를 요구해 404가 난다.
   */
  @GetMapping("/api/tutor/academy/lessons/{lessonId}/hero-image")
  public ResponseEntity<byte[]> myHeroImage(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId) {
    return imageResponse(academyMediaService.readMyHeroImage(principal, lessonId));
  }

  /**
   * 히어로 이미지 슬롯 업로드(V8 시안). slot = principal | textbook1 | textbook2 | textbook3.
   *
   * <p>수업상품 대표 이미지와 달리 대상이 Academy 하나라 업로드가 곧 저장이다.
   */
  @PostMapping(
      value = "/api/tutor/academy/media/hero/{slot}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> uploadHeroMedia(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String slot,
      @RequestPart("image") MultipartFile image) {
    academyMediaService.uploadHeroMedia(principal, HeroMediaSlot.from(slot), image);
    return ResponseEntity.noContent().build();
  }

  /** 히어로 이미지 슬롯 비우기. 시안 placeholder로 되돌아간다. */
  @DeleteMapping("/api/tutor/academy/media/hero/{slot}")
  public ResponseEntity<Void> deleteHeroMedia(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String slot) {
    academyMediaService.deleteHeroMedia(principal, HeroMediaSlot.from(slot));
    return ResponseEntity.noContent().build();
  }

  /** 공개 히어로 이미지. 응답 DTO에 storage key를 싣지 않으므로 이 경로로만 노출된다. */
  @GetMapping("/api/academies/{slug}/hero-media/{slot}")
  public ResponseEntity<byte[]> publicHeroMedia(
      @PathVariable String slug, @PathVariable String slot) {
    return imageResponse(
        academyMediaService.readPublicHeroMedia(slug, HeroMediaSlot.from(slot)));
  }

  /** 빌더 프리뷰용 히어로 이미지. 미발행(DRAFT) 학원도 렌더해야 해서 소유자 경로를 따로 둔다. */
  @GetMapping("/api/tutor/academy/hero-media/{slot}")
  public ResponseEntity<byte[]> myHeroMedia(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String slot) {
    return imageResponse(
        academyMediaService.readMyHeroMedia(principal, HeroMediaSlot.from(slot)));
  }

  /** 저장된 실제 포맷대로 Content-Type을 붙인다. 애니메이션 GIF는 image/gif로 내려가야 움직인다. */
  private static ResponseEntity<byte[]> imageResponse(AcademyMediaService.ServedImage image) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }
}
