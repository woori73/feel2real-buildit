package f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminVisibilityUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.dto.ExamAnalysisResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.dto.ExamAnalysisSearchCondition;
import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.service.ExamAnalysisService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ExamAnalysisController {

  private final ExamAnalysisService examAnalysisService;

  @GetMapping("/api/analyzes")
  public ResponseEntity<List<ExamAnalysisResponse>> searchPublic(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String schoolName,
      @RequestParam(required = false) Integer grade,
      @RequestParam(required = false) String semester,
      @RequestParam(required = false) String examType,
      @RequestParam(required = false) Integer subjectId) {
    return ResponseEntity.ok(examAnalysisService.searchPublic(
        new ExamAnalysisSearchCondition(keyword, schoolName, grade, semester, examType, subjectId)));
  }

  @GetMapping("/api/analyzes/latest")
  public ResponseEntity<List<ExamAnalysisResponse>> latestPublic(
      @RequestParam(defaultValue = "3") int limit) {
    return ResponseEntity.ok(examAnalysisService.latestPublic(limit));
  }

  @GetMapping("/api/analyzes/{id}")
  public ResponseEntity<ExamAnalysisResponse> getPublic(@PathVariable Long id) {
    return ResponseEntity.ok(examAnalysisService.getPublic(id));
  }

  @GetMapping(value = "/api/analyzes/{id}/content", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<byte[]> getPublicContent(@PathVariable Long id) {
    byte[] html = examAnalysisService.getPublicHtmlContent(id).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
        .header("Content-Security-Policy", "default-src 'self' https: data:; "
            + "style-src 'self' https: 'unsafe-inline'; "
            + "font-src 'self' https: data:; "
            + "img-src 'self' https: data:; "
            + "script-src 'unsafe-inline'; "
            + "connect-src 'none'; object-src 'none'; base-uri 'none'")
        .body(html);
  }

  @GetMapping("/api/admin/analyzes")
  public ResponseEntity<List<ExamAnalysisResponse>> listAdmin() {
    return ResponseEntity.ok(examAnalysisService.listAdmin());
  }

  @PostMapping(value = "/api/admin/analyzes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ExamAnalysisResponse> createAdmin(
      @RequestParam String title,
      @RequestParam String schoolName,
      @RequestParam Integer grade,
      @RequestParam String semester,
      @RequestParam String examType,
      @RequestParam Integer subjectId,
      @RequestParam(defaultValue = "true") boolean visible,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(examAnalysisService.create(title, schoolName, grade, semester, examType, subjectId, visible, file));
  }

  @PutMapping(value = "/api/admin/analyzes/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ExamAnalysisResponse> updateAdmin(
      @PathVariable Long id,
      @RequestParam String title,
      @RequestParam String schoolName,
      @RequestParam Integer grade,
      @RequestParam String semester,
      @RequestParam String examType,
      @RequestParam Integer subjectId,
      @RequestParam(defaultValue = "true") boolean visible,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return ResponseEntity.ok(examAnalysisService.update(id, title, schoolName, grade, semester, examType, subjectId, visible, file));
  }

  @PatchMapping("/api/admin/analyzes/{id}/visibility")
  public ResponseEntity<ExamAnalysisResponse> updateVisibility(
      @PathVariable Long id,
      @RequestBody AdminVisibilityUpdateRequest request) {
    return ResponseEntity.ok(examAnalysisService.updateVisibility(id, request.isVisible()));
  }

  @DeleteMapping("/api/admin/analyzes/{id}")
  public ResponseEntity<Void> softDelete(@PathVariable Long id) {
    examAnalysisService.softDelete(id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/admin/analyzes/{id}/hard")
  public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
    examAnalysisService.hardDelete(id);
    return ResponseEntity.noContent().build();
  }
}
