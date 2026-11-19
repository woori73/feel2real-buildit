package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.LessonForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.LessonView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.PhotoView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 학생부(수업 기록·채점 기록·채점 사진·약점 정리) API — ADMIN 전용(/api/admin/** 보안 규칙) + 사업체 접근 확인. 수정·삭제 가능.
 */
@RestController
@RequestMapping("/api/admin/easy/records")
public class EasyLessonRecordController {

  private final EasyLessonRecordService service;
  private final EasyTutorAccessService access;

  public EasyLessonRecordController(EasyLessonRecordService service, EasyTutorAccessService access) {
    this.service = service;
    this.access = access;
  }

  private void requireBusiness(AuthenticatedPrincipal p, Long businessId) {
    access.requireAllowedBusiness(p, businessId);
  }

  @GetMapping("/students/{studentId}")
  public Map<String, Object> book(@RequestParam Long businessId, @PathVariable Long studentId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.studentBook(businessId, studentId);
  }

  @PostMapping("/students/{studentId}/lessons")
  public LessonView createLesson(@RequestParam Long businessId, @PathVariable Long studentId,
      @RequestBody LessonForm form, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.createLesson(businessId, studentId, form, "WEB");
  }

  @PutMapping("/lessons/{lessonRecordId}")
  public LessonView updateLesson(@RequestParam Long businessId, @PathVariable Long lessonRecordId,
      @RequestBody LessonForm form, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.updateLesson(businessId, lessonRecordId, form);
  }

  @DeleteMapping("/lessons/{lessonRecordId}")
  public ResponseEntity<Void> deleteLesson(@RequestParam Long businessId, @PathVariable Long lessonRecordId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    service.deleteLesson(businessId, lessonRecordId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/students/{studentId}/gradings")
  public GradingView createGrading(@RequestParam Long businessId, @PathVariable Long studentId,
      @RequestBody GradingForm form, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.createGrading(businessId, studentId, form, "WEB");
  }

  @PutMapping("/gradings/{gradingId}")
  public GradingView updateGrading(@RequestParam Long businessId, @PathVariable Long gradingId,
      @RequestBody GradingForm form, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.updateGrading(businessId, gradingId, form);
  }

  @DeleteMapping("/gradings/{gradingId}")
  public ResponseEntity<Void> deleteGrading(@RequestParam Long businessId, @PathVariable Long gradingId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    service.deleteGrading(businessId, gradingId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/gradings/{gradingId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public PhotoView addPhoto(@RequestParam Long businessId, @PathVariable Long gradingId,
      @RequestParam(defaultValue = "STUDENT") String kind,
      @RequestPart("file") MultipartFile file, @AuthenticationPrincipal AuthenticatedPrincipal p) throws IOException {
    requireBusiness(p, businessId);
    if (file.getSize() > EasyLessonRecordService.MAX_PHOTO_BYTES) {
      throw new IllegalArgumentException("사진은 10MB 이하만 올릴 수 있습니다.");
    }
    return service.addPhoto(businessId, gradingId, file.getBytes(), kind, p.userId());
  }

  /** 답지 사진 보기. 학생 자료라 캐시하지 않는다. */
  @GetMapping("/photos/{photoId}")
  public ResponseEntity<byte[]> photo(@RequestParam Long businessId, @PathVariable Long photoId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) throws IOException {
    requireBusiness(p, businessId);
    var f = service.readPhoto(businessId, photoId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(f.contentType()))
        .header("X-Content-Type-Options", "nosniff").body(f.bytes());
  }

  public record WeaknessForm(String summary, java.util.List<EasyLessonRecordService.WeaknessTopic> topics) {}

  @PostMapping("/students/{studentId}/weakness")
  public EasyLessonRecordService.WeaknessView saveWeakness(@RequestParam Long businessId, @PathVariable Long studentId,
      @RequestBody WeaknessForm form, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.saveWeakness(businessId, studentId, form.summary(), form.topics(), "WEB");
  }

  @DeleteMapping("/weakness/{noteId}")
  public ResponseEntity<Void> deleteWeakness(@RequestParam Long businessId, @PathVariable Long noteId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    service.deleteWeakness(businessId, noteId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/photos/{photoId}")
  public ResponseEntity<Void> deletePhoto(@RequestParam Long businessId, @PathVariable Long photoId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    service.deletePhoto(businessId, photoId);
    return ResponseEntity.noContent().build();
  }
}
