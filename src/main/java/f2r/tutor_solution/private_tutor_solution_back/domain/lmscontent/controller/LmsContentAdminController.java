package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.AdminContentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.AdminContentsView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.ContentDetailsUpsert;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsContentAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * 강사용 LMS 콘텐츠 관리 API(작업지시서 §4). 경로가 {@code /api/tutor/**}라 SecurityConfig의
 * hasRole("TUTOR")를 그대로 탄다. "다른 학원 TUTOR"는 {@link LmsContentAdminService}의
 * {@code requireOwnedAcademyByHandle}가 막는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tutor/lms/{handle}/contents")
public class LmsContentAdminController {

  private final LmsContentAdminService service;

  @GetMapping
  public ResponseEntity<AdminContentsView> list(@PathVariable String handle) {
    return ResponseEntity.ok(service.list(handle));
  }

  /** 강좌 선택 + 메타데이터 + 영상 업로드를 한 번에 저장한다. multipart라 나머지 필드는 폼 파트로 받는다. */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AdminContentView> create(
      @PathVariable String handle,
      @RequestPart("video") MultipartFile video,
      @RequestParam("lessonId") Long lessonId,
      @RequestParam("title") String title,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "schoolName", required = false) String schoolName,
      @RequestParam(value = "gradeLabel", required = false) String gradeLabel,
      @RequestParam(value = "examName", required = false) String examName,
      @RequestParam(value = "unitLabel", required = false) String unitLabel,
      @RequestParam(value = "displayOrder", required = false) Integer displayOrder) {
    ContentDetailsUpsert details = new ContentDetailsUpsert(
        lessonId, title, description, schoolName, gradeLabel, examName, unitLabel, displayOrder);
    return ResponseEntity.ok(service.createWithVideo(handle, details, video));
  }

  @PutMapping("/{lmsContentId}")
  public ResponseEntity<AdminContentView> updateDetails(
      @PathVariable String handle, @PathVariable Long lmsContentId,
      @RequestBody ContentDetailsUpsert details) {
    return ResponseEntity.ok(service.updateDetails(handle, lmsContentId, details));
  }

  @PatchMapping("/{lmsContentId}/publish")
  public ResponseEntity<AdminContentView> setPublished(
      @PathVariable String handle, @PathVariable Long lmsContentId,
      @RequestBody PublishRequest request) {
    return ResponseEntity.ok(
        service.setPublished(handle, lmsContentId, request != null && request.published()));
  }

  @DeleteMapping("/{lmsContentId}")
  public ResponseEntity<Void> delete(@PathVariable String handle, @PathVariable Long lmsContentId) {
    service.delete(handle, lmsContentId);
    return ResponseEntity.noContent().build();
  }

  public record PublishRequest(boolean published) {}
}
