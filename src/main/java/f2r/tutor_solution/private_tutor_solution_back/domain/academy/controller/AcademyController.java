package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyListResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVisitStatisticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionSaveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionSaveResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyPageLayoutSchema;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageLayoutService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVisitStatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.LessonProductService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.LessonCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AcademyController {

  private final AcademyService academyService;
  private final LessonProductService lessonProductService;
  private final LessonCollectionService lessonCollectionService;
  private final AcademyPageLayoutService academyPageLayoutService;
  private final AcademyVisitStatisticsService academyVisitStatisticsService;

  @GetMapping("/api/academies")
  public ResponseEntity<List<AcademyListResponse>> listPublicAcademies(
      @RequestParam(required = false) Integer subjectId,
      @RequestParam(required = false) TargetGroup targetGroup,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String district) {
    return ResponseEntity.ok(academyService.listPublic(subjectId, targetGroup, city, district));
  }

  @GetMapping("/api/academies/{slug}")
  public ResponseEntity<AcademyResponse> getPublicAcademy(@PathVariable String slug) {
    return ResponseEntity.ok(academyService.getPublic(slug));
  }

  @GetMapping("/api/academies/{slug}/visits")
  public ResponseEntity<AcademyVisitStatisticsResponse> getVisitStatistics(
      @PathVariable String slug) {
    return ResponseEntity.ok(academyVisitStatisticsService.getPublicSummary(slug));
  }

  @PostMapping("/api/academies/{slug}/visits")
  public ResponseEntity<AcademyVisitStatisticsResponse> recordVisit(
      @PathVariable String slug,
      HttpServletRequest request) {
    return ResponseEntity.ok(
        academyVisitStatisticsService.recordPublicVisit(slug, request.getSession(true)));
  }

  @GetMapping("/api/academies/{slug}/logo")
  public ResponseEntity<byte[]> getPublicLogo(@PathVariable String slug) {
    return imageResponse(academyService.readPublicLogo(slug));
  }

  @GetMapping("/api/academies/{slug}/cover")
  public ResponseEntity<byte[]> getPublicCover(@PathVariable String slug) {
    return imageResponse(academyService.readPublicCover(slug));
  }

  private static ResponseEntity<byte[]> imageResponse(AcademyService.ServedImage image) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  /**
   * 공개 Academy 페이지 레이아웃(해석된 schema + blocks). React 공개 preview 렌더용.
   *
   * <p>저장 레이아웃이 없는 레거시 Academy도 {@link AcademyPageLayoutService#resolveLayoutSchema}가
   * 기본 레이아웃을 결정적으로 만들어 준다. 기존 SSR {@code /academies/{slug}/preview}와 동일한 데이터.
   */
  @GetMapping("/api/academies/{slug}/layout")
  public ResponseEntity<AcademyPageLayoutSchema> getPublicLayout(@PathVariable String slug) {
    Academy academy = academyService.getPublishedEntity(slug);
    return ResponseEntity.ok(academyPageLayoutService.resolveLayoutSchema(academy));
  }

  @GetMapping("/api/tutor/academy/handle-available")
  public ResponseEntity<Map<String, Boolean>> checkHandleAvailable(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam String handle) {
    boolean available = academyService.isHandleAvailable(principal, handle);
    return ResponseEntity.ok(Map.of("available", available));
  }

  @GetMapping("/api/tutor/academy/me")
  public ResponseEntity<AcademyResponse> getMyAcademy(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService.getMine(principal)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  @PostMapping(value = "/api/tutor/academy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AcademyResponse> createAcademy(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("academy") AcademyUpsertRequest request,
      @RequestPart(value = "logo", required = false) MultipartFile logo,
      @RequestPart(value = "cover", required = false) MultipartFile cover) {
    return ResponseEntity.ok(academyService.create(principal, request, logo, cover));
  }

  /**
   * 로고 파일 없이 폼 값(JSON)만으로 Academy 생성. 랜딩 발행 폼 및 향후 LLM 클라이언트용.
   * multipart {@link #createAcademy}와 동일한 경로/서비스(create)를 Content-Type으로 분기한다.
   * 로고는 서버 기본 로고로 채워지며, 이후 multipart {@code PUT}으로 교체 가능하다.
   */
  @PostMapping(value = "/api/tutor/academy", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AcademyResponse> createAcademyJson(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AcademyUpsertRequest request) {
    return ResponseEntity.ok(academyService.create(principal, request, null, null));
  }

  @PutMapping(value = "/api/tutor/academy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AcademyResponse> updateAcademy(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("academy") AcademyUpsertRequest request,
      @RequestPart(value = "logo", required = false) MultipartFile logo,
      @RequestPart(value = "cover", required = false) MultipartFile cover) {
    return ResponseEntity.ok(academyService.update(principal, request, logo, cover));
  }


  @PutMapping(value = "/api/tutor/academy", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AcademyResponse> updateAcademyJson(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AcademyUpsertRequest request) {
    return ResponseEntity.ok(academyService.update(principal, request, null, null));
  }

  @PutMapping("/api/tutor/academy/page-settings")
  public ResponseEntity<AcademyResponse> updatePageSettings(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AcademyPageSettingsUpdateRequest request) {
    return ResponseEntity.ok(academyService.updatePageSettings(principal, request));
  }

  @DeleteMapping("/api/tutor/academy")
  public ResponseEntity<Void> deleteAcademy(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    academyService.delete(principal);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/tutor/academy/lessons/{lessonId}")
  public ResponseEntity<LessonResponse> getLesson(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId) {
    return ResponseEntity.ok(lessonProductService.getMine(principal, lessonId));
  }

  @PostMapping("/api/tutor/academy/lessons")
  public ResponseEntity<LessonResponse> createLesson(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody LessonUpsertRequest request) {
    return ResponseEntity.ok(lessonProductService.create(principal, request));
  }

  @PutMapping("/api/tutor/academy/lessons/{lessonId}")
  public ResponseEntity<LessonResponse> updateLesson(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId,
      @RequestBody LessonUpsertRequest request) {
    return ResponseEntity.ok(lessonProductService.update(principal, lessonId, request));
  }

  @PutMapping("/api/tutor/academy/lessons/collection")
  public ResponseEntity<LessonCollectionSaveResponse> saveLessonCollection(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody LessonCollectionSaveRequest request) {
    return ResponseEntity.ok(lessonCollectionService.save(principal, request));
  }

  @DeleteMapping("/api/tutor/academy/lessons/{lessonId}")
  public ResponseEntity<Void> deleteLesson(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long lessonId) {
    lessonProductService.hide(principal, lessonId);
    return ResponseEntity.noContent().build();
  }
}
