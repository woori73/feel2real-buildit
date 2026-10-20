package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminVisibilityUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.WikiDownloadRecordingService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto.WikiExamOriginalResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service.WikiExamOriginalService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service.WikiExamOriginalService.DownloadFile;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
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

/** BuildIt Wiki 모의고사 원본 API. 공개 목록 + 회원 다운로드 + ADMIN CRUD. */
@RestController
@RequiredArgsConstructor
public class WikiExamOriginalController {

  private final WikiExamOriginalService service;
  private final WikiDownloadRecordingService downloadRecordingService;

  @GetMapping("/api/wiki/originals")
  public ResponseEntity<List<WikiExamOriginalResponse>> listPublic(
      @RequestParam(required = false) Integer grade,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) String docType) {
    if (year == null && month == null && docType == null) {
      return ResponseEntity.ok(service.searchPublic(grade));
    }
    return ResponseEntity.ok(service.searchPublicFiltered(grade, year, month, docType));
  }

  @GetMapping("/api/wiki/originals/{id}/download")
  public ResponseEntity<byte[]> download(
      @PathVariable Long id, @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    DownloadFile file = service.getPublicDownload(id);
    downloadRecordingService.record(WikiMaterialCategory.ORIGINAL, id, file.title(),
        principal.userId(), principal.role());
    // 항상 attachment(브라우저 뷰어 X) + 확장자로 content-type 추론(pdf/hwp/hwpx) + RFC5987 파일명.
    return FileDownloadResponses.attachment(
        file.bytes(), DocumentUploadValidator.contentType(file.fileName()), file.fileName());
  }

  /**
   * 인라인 열람 (HTML 해설지·이미지). 다운로드는 항상 attachment지만, 열람은 브라우저 표시용이다.
   * 업로드된 HTML의 스크립트가 본 사이트 세션에서 실행되지 않도록 CSP sandbox로 격리하고,
   * nosniff로 콘텐츠 타입 위장을 막는다. 파일 응답은 회원 전용 정책(다운로드와 동일)을 유지한다.
   */
  @GetMapping("/api/wiki/originals/{id}/view")
  public ResponseEntity<byte[]> view(
      @PathVariable Long id, @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    DownloadFile file = service.getPublicView(id);
    String contentType = DocumentUploadValidator.contentType(file.fileName());
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.parseMediaType(contentType));
    headers.setContentDisposition(
        org.springframework.http.ContentDisposition.inline()
            .filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8)
            .build());
    // sandbox + allow-scripts allow-modals: 해설 HTML의 문항 이동·글자 크기 조절 스크립트와
    // 인쇄(window.print)가 동작하게 하되, allow-same-origin이 없어 불투명 출처(opaque origin)로
    // 격리된다 — 사이트 쿠키·스토리지·DOM에 접근할 수 없고 폼 제출·팝업·상위 이동은 계속 차단된다.
    headers.set("Content-Security-Policy", "sandbox allow-scripts allow-modals");
    headers.set("X-Content-Type-Options", "nosniff");
    headers.setCacheControl("private, no-store");
    return new ResponseEntity<>(file.bytes(), headers, HttpStatus.OK);
  }

  @GetMapping("/api/admin/wiki/originals")
  public ResponseEntity<List<WikiExamOriginalResponse>> listAdmin() {
    return ResponseEntity.ok(service.listAdmin());
  }

  @PostMapping(value = "/api/admin/wiki/originals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<WikiExamOriginalResponse> createAdmin(
      @RequestParam String title,
      @RequestParam Integer grade,
      @RequestParam(required = false) String examLabel,
      @RequestParam String docType,
      @RequestParam(defaultValue = "true") boolean visible,
      @RequestParam(required = false) Integer examYear,
      @RequestParam(required = false) Integer examMonth,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(
        service.create(title, grade, examLabel, docType, visible, file, examYear, examMonth));
  }

  @PutMapping(value = "/api/admin/wiki/originals/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<WikiExamOriginalResponse> updateAdmin(
      @PathVariable Long id,
      @RequestParam String title,
      @RequestParam Integer grade,
      @RequestParam(required = false) String examLabel,
      @RequestParam String docType,
      @RequestParam(defaultValue = "true") boolean visible,
      @RequestParam(required = false) Integer examYear,
      @RequestParam(required = false) Integer examMonth,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return ResponseEntity.ok(
        service.update(id, title, grade, examLabel, docType, visible, file, examYear, examMonth));
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
