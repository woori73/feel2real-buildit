package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class StudioController {
  private final StudioService service;
  private final StudioBulkImport bulk;
  private final java.util.concurrent.Semaphore originalReads =
      new java.util.concurrent.Semaphore(2);

  @org.springframework.beans.factory.annotation.Value(
      "${app.studio.public-base-url:${app.mcp.public-base-url:https://feel2real.co.kr}}")
  private String publicBase;

  @ModelAttribute
  public void protectWrites(
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "private, no-store");
    if (!"GET".equals(request.getMethod())
        && !"HEAD".equals(request.getMethod())
        && !publicBase.equals(request.getHeader("Origin")))
      throw StudioService.error(403, "동일 출처의 제작 화면에서 작업하세요.");
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  @ResponseBody
  public ResponseEntity<?> error(org.springframework.web.server.ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", e.getReason() == null ? "요청을 확인하세요." : e.getReason()));
  }

  @ExceptionHandler(IOException.class)
  @ResponseBody
  public ResponseEntity<?> fileError(IOException e) {
    return ResponseEntity.status(422)
        .body(Map.of("message", "파일을 읽을 수 없습니다. 원본 형식과 저장소 상태를 확인하세요."));
  }

  @GetMapping("/ai-agent-studio")
  public String page(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    service.require(p, false, false);
    return "studio/index";
  }

  @GetMapping("/api/studio/project")
  @ResponseBody
  public Object project(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.overview(p);
  }

  @PostMapping("/api/studio/project")
  @ResponseBody
  public Object initialize(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.initialize(p);
  }

  @GetMapping("/api/studio/revisions")
  @ResponseBody
  public Object revisions(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(required = false) Integer episode,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "200") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return service.revisions(p, episode, kind, q, limit, offset);
  }

  @GetMapping("/api/studio/revisions/{id}")
  @ResponseBody
  public Object revision(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id) {
    return service.revision(p, id);
  }

  @PostMapping("/api/studio/revisions")
  @ResponseBody
  public Object save(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody StudioService.Revision r) {
    return service.save(p, r);
  }

  public record Restore(String expectedHead, String label) {}

  @PostMapping("/api/studio/revisions/{id}/restore")
  @ResponseBody
  public Object restore(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody Restore r) {
    return service.restore(p, id, r.expectedHead(), r.label());
  }

  @GetMapping("/api/studio/cuts")
  @ResponseBody
  public Object cuts(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(defaultValue = "1") int episode) {
    return service.cuts(p, episode);
  }

  public record Bulk(String fileId) {}

  @PostMapping("/api/studio/imports/ep01")
  @ResponseBody
  public Object bulk(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody Bulk r)
      throws IOException {
    return bulk.importEp01(p, r.fileId());
  }

  public record Review(String action, String note) {}

  @PostMapping("/api/studio/revisions/{id}/review")
  @ResponseBody
  public void review(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody Review r) {
    service.review(p, id, r.action(), r.note());
  }

  @GetMapping("/api/studio/compare")
  @ResponseBody
  public Object compare(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam String left,
      @RequestParam String right) {
    return Map.of("left", service.revision(p, left), "right", service.revision(p, right));
  }

  @GetMapping("/api/studio/files")
  @ResponseBody
  public Object files(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.files(p);
  }

  @PostMapping("/api/studio/files")
  @ResponseBody
  public Object upload(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam MultipartFile file)
      throws IOException {
    return service.upload(p, file);
  }

  @GetMapping("/api/studio/files/{id}")
  @ResponseBody
  public Object metadata(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id) {
    var f = new LinkedHashMap<>(service.file(p, id));
    f.remove("storage_key");
    return f;
  }

  @GetMapping("/api/studio/files/{id}/content")
  public void content(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      jakarta.servlet.http.HttpServletResponse response)
      throws IOException {
    var f = service.file(p, id);
    if (!originalReads.tryAcquire())
      throw StudioService.error(429, "다른 원본 열람이 진행 중입니다. 잠시 후 다시 시도하세요.");
    try {
      byte[] bytes = service.bytes(p, id);
      response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
      response.setContentLengthLong(bytes.length);
      response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
      response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
      response.setHeader(
          HttpHeaders.CONTENT_DISPOSITION,
          ContentDisposition.attachment()
              .filename((String) f.get("filename"), StandardCharsets.UTF_8)
              .build()
              .toString());
      response.getOutputStream().write(bytes);
      response.flushBuffer();
    } finally {
      originalReads.release();
    }
  }

  @GetMapping("/api/studio/files/{id}/text")
  @ResponseBody
  public Object text(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id)
      throws IOException {
    var f = service.file(p, id);
    String ext = (String) f.get("extension");
    if (((Number) f.get("size_bytes")).longValue() > 20L * 1024 * 1024)
      throw StudioService.error(413, "20 MiB 초과 자료는 원본 다운로드로 열람하세요.");
    byte[] bytes = service.bytes(p, id);
    if (ext.equals("pdf")) {
      try (var document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
        var stripper = new org.apache.pdfbox.text.PDFTextStripper();
        stripper.setEndPage(Math.min(100, document.getNumberOfPages()));
        String text = stripper.getText(document);
        return Map.of(
            "text",
            text.substring(0, Math.min(200000, text.length())),
            "pages",
            document.getNumberOfPages(),
            "note",
            "처음 100페이지·20만 자까지 텍스트를 표시합니다. 스캔 PDF의 이미지 원문은 다운로드로 확인하세요.");
      }
    }
    if (Set.of("md", "html", "json").contains(ext))
      return Map.of(
          "text",
          new String(bytes, StandardCharsets.UTF_8)
              .substring(0, Math.min(200000, new String(bytes, StandardCharsets.UTF_8).length())),
          "note",
          "원본 텍스트 · 최대 20만 자");
    if (ext.equals("zip")) {
      var temp = java.nio.file.Files.createTempFile("f2r-zip-preview-", ".zip");
      try {
        java.nio.file.Files.write(temp, bytes);
        try (var zip = new java.util.zip.ZipFile(temp.toFile())) {
          return Map.of(
              "entries",
              zip.stream()
                  .limit(1000)
                  .map(
                      e ->
                          Map.of(
                              "name",
                              e.getName(),
                              "size",
                              e.getSize(),
                              "compressedSize",
                              e.getCompressedSize()))
                  .toList(),
              "note",
              "목록만 조회합니다. 압축을 해제하거나 실행하지 않습니다.");
        }
      } finally {
        java.nio.file.Files.deleteIfExists(temp);
      }
    }
    throw StudioService.error(400, "이미지·영상은 미디어 열람을 이용하세요.");
  }

  @GetMapping("/api/studio/events")
  @ResponseBody
  public Object events(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.events(p);
  }

  @GetMapping("/api/studio/members")
  @ResponseBody
  public Object members(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.members(p);
  }

  public record Member(long userId, String permission) {}

  @PostMapping("/api/studio/members")
  @ResponseBody
  public void member(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody Member m) {
    service.member(p, m.userId(), m.permission());
  }
}
