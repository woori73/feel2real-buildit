package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermAdminDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermCampaignAdminService;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 운영자 캠페인 관리 API. 경로가 {@code /api/tutor/**} 라 {@code SecurityConfig} 의
 * {@code hasRole("TUTOR")} 를 그대로 탄다. "다른 학원 TUTOR" 와 ADMIN 우회는
 * {@link MidtermCampaignAdminService} 의 {@code requireOwnedAcademyByHandle} 가 막는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tutor/lms/{handle}/midterm-campaign")
public class MidtermCampaignAdminController {

  private final MidtermCampaignAdminService service;

  @GetMapping
  public ResponseEntity<AdminView> get(@PathVariable String handle) {
    return ResponseEntity.ok(service.getView(handle));
  }

  @PutMapping
  public ResponseEntity<AdminView> upsertCampaign(
      @PathVariable String handle, @RequestBody CampaignUpsert req) {
    return ResponseEntity.ok(service.upsertCampaign(handle, req));
  }

  @PatchMapping("/publish")
  public ResponseEntity<AdminView> publish(
      @PathVariable String handle, @RequestBody PublishRequest req) {
    return ResponseEntity.ok(service.setPublished(handle, req.published()));
  }

  // ── 이미지: 업로드+연결이 한 번의 서버 호출 (raw key 입력 불가) ─────────
  @PostMapping(value = "/popup-visual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AdminView> uploadPopupVisual(
      @PathVariable String handle, @RequestPart("image") MultipartFile image) {
    return ResponseEntity.ok(service.uploadPopupVisual(handle, image));
  }

  @DeleteMapping("/popup-visual")
  public ResponseEntity<AdminView> clearPopupVisual(@PathVariable String handle) {
    return ResponseEntity.ok(service.clearPopupVisual(handle));
  }

  @PostMapping(value = "/materials/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AdminView> uploadMaterialThumbnail(
      @PathVariable String handle, @PathVariable Long id, @RequestPart("image") MultipartFile image) {
    return ResponseEntity.ok(service.uploadMaterialThumbnail(handle, id, image));
  }

  @DeleteMapping("/materials/{id}/thumbnail")
  public ResponseEntity<AdminView> clearMaterialThumbnail(
      @PathVariable String handle, @PathVariable Long id) {
    return ResponseEntity.ok(service.clearMaterialThumbnail(handle, id));
  }

  @PostMapping(value = "/making-of/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AdminView> uploadMakingOfImage(
      @PathVariable String handle, @PathVariable Long id, @RequestPart("image") MultipartFile image) {
    return ResponseEntity.ok(service.uploadMakingOfImage(handle, id, image));
  }

  @DeleteMapping("/making-of/{id}/image")
  public ResponseEntity<AdminView> clearMakingOfImage(
      @PathVariable String handle, @PathVariable Long id) {
    return ResponseEntity.ok(service.clearMakingOfImage(handle, id));
  }

  // ── 학교 컬렉션 ─────────────────────────────────────────────────────────
  @PostMapping("/school-collections")
  public ResponseEntity<AdminView> createSchoolCollection(
      @PathVariable String handle, @RequestBody SchoolCollectionUpsert req) {
    return ResponseEntity.ok(service.createSchoolCollection(handle, req));
  }

  @PutMapping("/school-collections/{id}")
  public ResponseEntity<AdminView> updateSchoolCollection(
      @PathVariable String handle, @PathVariable Long id, @RequestBody SchoolCollectionUpsert req) {
    return ResponseEntity.ok(service.updateSchoolCollection(handle, id, req));
  }

  @DeleteMapping("/school-collections/{id}")
  public ResponseEntity<AdminView> deleteSchoolCollection(
      @PathVariable String handle, @PathVariable Long id) {
    return ResponseEntity.ok(service.deleteSchoolCollection(handle, id));
  }

  @PutMapping("/school-collections/{id}/briefing")
  public ResponseEntity<AdminView> updateBriefing(
      @PathVariable String handle, @PathVariable Long id, @RequestBody BriefingUpsert req) {
    return ResponseEntity.ok(service.updateBriefing(handle, id, req));
  }

  @PutMapping("/school-collections/{id}/prep-status")
  public ResponseEntity<AdminView> replacePrepStatuses(
      @PathVariable String handle, @PathVariable Long id, @RequestBody PrepStatusReplace req) {
    return ResponseEntity.ok(service.replacePrepStatuses(handle, id, req));
  }

  @PutMapping("/school-collections/{id}/after-exam")
  public ResponseEntity<AdminView> upsertAfterExam(
      @PathVariable String handle, @PathVariable Long id, @RequestBody AfterExamUpsert req) {
    return ResponseEntity.ok(service.upsertAfterExam(handle, id, req));
  }

  // ── 자료 Showcase ───────────────────────────────────────────────────────
  @PostMapping("/materials")
  public ResponseEntity<AdminView> createMaterial(
      @PathVariable String handle, @RequestBody MaterialUpsert req) {
    return ResponseEntity.ok(service.createMaterial(handle, req));
  }

  @PutMapping("/materials/{id}")
  public ResponseEntity<AdminView> updateMaterial(
      @PathVariable String handle, @PathVariable Long id, @RequestBody MaterialUpsert req) {
    return ResponseEntity.ok(service.updateMaterial(handle, id, req));
  }

  @DeleteMapping("/materials/{id}")
  public ResponseEntity<AdminView> deleteMaterial(
      @PathVariable String handle, @PathVariable Long id) {
    return ResponseEntity.ok(service.deleteMaterial(handle, id));
  }

  // ── 업데이트 로그 ───────────────────────────────────────────────────────
  @PostMapping("/update-logs")
  public ResponseEntity<AdminView> createUpdateLog(
      @PathVariable String handle, @RequestBody UpdateLogUpsert req) {
    return ResponseEntity.ok(service.createUpdateLog(handle, req));
  }

  @PutMapping("/update-logs/{id}")
  public ResponseEntity<AdminView> updateUpdateLog(
      @PathVariable String handle, @PathVariable Long id, @RequestBody UpdateLogUpsert req) {
    return ResponseEntity.ok(service.updateUpdateLog(handle, id, req));
  }

  @DeleteMapping("/update-logs/{id}")
  public ResponseEntity<AdminView> deleteUpdateLog(
      @PathVariable String handle, @PathVariable Long id) {
    return ResponseEntity.ok(service.deleteUpdateLog(handle, id));
  }

  // ── Making-of ──────────────────────────────────────────────────────────
  @PostMapping("/making-of")
  public ResponseEntity<AdminView> createMakingOf(
      @PathVariable String handle, @RequestBody MakingOfUpsert req) {
    return ResponseEntity.ok(service.createMakingOf(handle, req));
  }

  @PutMapping("/making-of/{id}")
  public ResponseEntity<AdminView> updateMakingOf(
      @PathVariable String handle, @PathVariable Long id, @RequestBody MakingOfUpsert req) {
    return ResponseEntity.ok(service.updateMakingOf(handle, id, req));
  }

  @DeleteMapping("/making-of/{id}")
  public ResponseEntity<AdminView> deleteMakingOf(
      @PathVariable String handle, @PathVariable Long id) {
    return ResponseEntity.ok(service.deleteMakingOf(handle, id));
  }

  // ── Next Exam CTA ──────────────────────────────────────────────────────
  @PutMapping("/next-exam")
  public ResponseEntity<AdminView> upsertNextExam(
      @PathVariable String handle, @RequestBody NextExamUpsert req) {
    return ResponseEntity.ok(service.upsertNextExam(handle, req));
  }
}
