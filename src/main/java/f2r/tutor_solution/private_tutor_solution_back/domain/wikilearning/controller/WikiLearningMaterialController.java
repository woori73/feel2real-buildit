package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.WikiDownloadRecordingService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.dto.WikiLearningMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.service.WikiLearningMaterialService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.service.WikiLearningMaterialService.DownloadFile;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.web.FileDownloadResponses;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class WikiLearningMaterialController {
  private final WikiLearningMaterialService service;
  private final WikiDownloadRecordingService downloadRecordingService;

  @GetMapping("/api/wiki/learning-materials")
  public ResponseEntity<List<WikiLearningMaterialResponse>> listPublic(@RequestParam(required = false) Integer grade) {
    return ResponseEntity.ok(service.searchPublic(grade));
  }
  @PostMapping(value = "/api/wiki/learning-materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<WikiLearningMaterialResponse> upload(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam String dataType, @RequestParam Integer grade,
      @RequestParam(required = false) String title, @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(service.upload(principal.userId(), dataType, grade, title, file));
  }
  @GetMapping("/api/wiki/learning-materials/{id}")
  public ResponseEntity<WikiLearningMaterialResponse> detail(@PathVariable Long id) { return ResponseEntity.ok(service.getDetail(id)); }

  /**
   * Public inline preview for PDF or validated HTML. The frontend iframe is sandboxed without
   * allow-same-origin/allow-scripts. CSP additionally blocks scripts, network requests, forms and
   * plugins while allowing inline CSS and data/blob images used by self-contained learning HTML.
   */
  @GetMapping("/api/wiki/learning-materials/{id}/preview")
  public ResponseEntity<byte[]> preview(@PathVariable Long id) {
    DownloadFile file = service.getPreview(id);
    boolean html = WikiLearningMaterialResponse.HTML_CONTENT_TYPE.equals(file.contentType());
    String contentType = html ? WikiLearningMaterialResponse.HTML_CONTENT_TYPE : WikiLearningMaterialResponse.PDF_CONTENT_TYPE;
    String fileName = html ? "preview.html" : "preview.pdf";
    String csp = html
        ? "default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; frame-ancestors 'self'; base-uri 'none'; form-action 'none'; object-src 'none'; script-src 'none'; connect-src 'none'"
        : "default-src 'none'; frame-ancestors 'self'; object-src 'self'";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, contentType + (html ? "; charset=UTF-8" : ""))
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", csp)
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
        .body(file.bytes());
  }

  @GetMapping("/api/wiki/learning-materials/{id}/download")
  public ResponseEntity<byte[]> download(
      @PathVariable Long id, @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    DownloadFile file = service.getDownload(id);
    downloadRecordingService.record(WikiMaterialCategory.LEARNING, id, file.title(),
        principal == null ? null : principal.userId(), principal == null ? null : principal.role());
    return FileDownloadResponses.attachment(file.bytes(), file.contentType(), file.fileName());
  }
  @GetMapping("/api/admin/wiki/learning-materials")
  public ResponseEntity<List<WikiLearningMaterialResponse>> listAdmin() { return ResponseEntity.ok(service.listAdmin()); }
  @DeleteMapping("/api/admin/wiki/learning-materials/{id}")
  public ResponseEntity<Void> adminDelete(@PathVariable Long id) { service.adminHardDelete(id); return ResponseEntity.noContent().build(); }
}
