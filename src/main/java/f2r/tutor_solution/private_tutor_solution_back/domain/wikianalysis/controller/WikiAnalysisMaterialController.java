package f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminVisibilityUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.WikiDownloadRecordingService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.dto.WikiAnalysisMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService.DownloadFile;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.web.FileDownloadResponses;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** BuildIt Wiki 분석 자료 API. 공개 목록 + 회원 다운로드 + ADMIN CRUD. */
@RestController
@RequiredArgsConstructor
public class WikiAnalysisMaterialController {

  private final WikiAnalysisMaterialService service;
  private final WikiDownloadRecordingService downloadRecordingService;

  @GetMapping("/api/wiki/analysis-materials")
  public ResponseEntity<List<WikiAnalysisMaterialResponse>> listPublic(
      @RequestParam(required = false) Integer grade) {
    return ResponseEntity.ok(service.searchPublic(grade));
  }

  @GetMapping("/api/wiki/analysis-materials/{id}/download")
  public ResponseEntity<byte[]> download(
      @PathVariable Long id, @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    DownloadFile file = service.getPublicDownload(id);
    downloadRecordingService.record(WikiMaterialCategory.ANALYSIS, id, file.title(),
        principal.userId(), principal.role());
    return FileDownloadResponses.attachment(file.bytes(), file.contentType(), file.fileName());
  }

  @GetMapping("/api/admin/wiki/analysis-materials")
  public ResponseEntity<List<WikiAnalysisMaterialResponse>> listAdmin() {
    return ResponseEntity.ok(service.listAdmin());
  }

  @PostMapping(value = "/api/admin/wiki/analysis-materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<WikiAnalysisMaterialResponse> createAdmin(
      @RequestParam String title,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String schoolName,
      @RequestParam Integer grade,
      @RequestParam Integer year,
      @RequestParam String semester,
      @RequestParam String examType,
      @RequestParam(required = false) String subject,
      @RequestParam(defaultValue = "true") boolean visible,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(
        service.create(title, description, schoolName, grade, year, semester, examType, subject, visible, file));
  }

  @PutMapping(value = "/api/admin/wiki/analysis-materials/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<WikiAnalysisMaterialResponse> updateAdmin(
      @PathVariable Long id,
      @RequestParam String title,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String schoolName,
      @RequestParam Integer grade,
      @RequestParam Integer year,
      @RequestParam String semester,
      @RequestParam String examType,
      @RequestParam(required = false) String subject,
      @RequestParam(defaultValue = "true") boolean visible,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return ResponseEntity.ok(
        service.update(id, title, description, schoolName, grade, year, semester, examType, subject, visible, file));
  }

  @PatchMapping("/api/admin/wiki/analysis-materials/{id}/visibility")
  public ResponseEntity<WikiAnalysisMaterialResponse> updateVisibility(
      @PathVariable Long id,
      @RequestBody AdminVisibilityUpdateRequest request) {
    return ResponseEntity.ok(service.updateVisibility(id, request.isVisible()));
  }

  @DeleteMapping("/api/admin/wiki/analysis-materials/{id}")
  public ResponseEntity<Void> softDelete(@PathVariable Long id) {
    service.softDelete(id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/admin/wiki/analysis-materials/{id}/hard")
  public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
    service.hardDelete(id);
    return ResponseEntity.noContent().build();
  }
}
