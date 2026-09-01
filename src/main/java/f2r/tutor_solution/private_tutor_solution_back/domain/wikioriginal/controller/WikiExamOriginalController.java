package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminVisibilityUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto.WikiExamOriginalResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service.WikiExamOriginalService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service.WikiExamOriginalService.DownloadFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 공개 BuildIt Wiki 모의고사 원본 API. 공개 목록/다운로드 + ADMIN CRUD. */
@RestController
@RequiredArgsConstructor
public class WikiExamOriginalController {

  private final WikiExamOriginalService service;

  @GetMapping("/api/wiki/originals")
  public ResponseEntity<List<WikiExamOriginalResponse>> listPublic(
      @RequestParam(required = false) Integer grade) {
    return ResponseEntity.ok(service.searchPublic(grade));
  }

  @GetMapping("/api/wiki/originals/latest")
  public ResponseEntity<List<WikiExamOriginalResponse>> latestPublic(
      @RequestParam(defaultValue = "6") int limit) {
    return ResponseEntity.ok(service.latestPublic(limit));
  }

  @GetMapping("/api/wiki/originals/{id}/download")
  public ResponseEntity<byte[]> download(@PathVariable Long id) {
    DownloadFile file = service.getPublicDownload(id);
    String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
        .body(file.bytes());
  }

  @GetMapping("/api/admin/wiki/originals")
  public ResponseEntity<List<WikiExamOriginalResponse>> listAdmin() {
    return ResponseEntity.ok(service.listAdmin());
  }

  @PostMapping(value = "/api/admin/wiki/originals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<WikiExamOriginalResponse> createAdmin(
      @RequestParam String title,
      @RequestParam Integer grade,
      @RequestParam String examLabel,
      @RequestParam String docType,
      @RequestParam(defaultValue = "true") boolean visible,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(service.create(title, grade, examLabel, docType, visible, file));
  }

  @PutMapping(value = "/api/admin/wiki/originals/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<WikiExamOriginalResponse> updateAdmin(
      @PathVariable Long id,
      @RequestParam String title,
      @RequestParam Integer grade,
      @RequestParam String examLabel,
      @RequestParam String docType,
      @RequestParam(defaultValue = "true") boolean visible,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return ResponseEntity.ok(service.update(id, title, grade, examLabel, docType, visible, file));
  }

  @PatchMapping("/api/admin/wiki/originals/{id}/visibility")
  public ResponseEntity<WikiExamOriginalResponse> updateVisibility(
      @PathVariable Long id,
      @RequestBody AdminVisibilityUpdateRequest request) {
    return ResponseEntity.ok(service.updateVisibility(id, request.isVisible()));
  }

  @DeleteMapping("/api/admin/wiki/originals/{id}")
  public ResponseEntity<Void> softDelete(@PathVariable Long id) {
    service.softDelete(id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/admin/wiki/originals/{id}/hard")
  public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
    service.hardDelete(id);
    return ResponseEntity.noContent().build();
  }
}
