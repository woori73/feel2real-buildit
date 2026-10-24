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
  private final StudioPreview previews;
  private final StudioProductionImporter importer;
  private final StudioCollaborationService collaboration;
  private final StudioProjectService projects;
  private final StudioAccessGuard accessGuard;
  private final StudioAiService ai;
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

  /** 홈·대시보드 링크 표시용 본인 권한 확인. 로그인한 누구나 200으로 자신의 여부만 받는다. */
  @GetMapping("/api/studio-access")
  @ResponseBody
  public Map<String, Object> access(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return Map.of("allowed", accessGuard.isAllowed(p));
  }

  @GetMapping("/studio-projects")
  public String projectsPage(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    projects.capabilities(p);
    return "studio/projects";
  }

  @GetMapping("/api/studio/projects")
  @ResponseBody
  public Object listProjects(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return projects.projects(p);
  }

  @GetMapping("/api/studio/project-capabilities")
  @ResponseBody
  public Object projectCapabilities(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return projects.capabilities(p);
  }

  public record NewProject(String title) {}

  @PostMapping("/api/studio/projects")
  @ResponseBody
  public Object createProject(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody NewProject r) {
    return projects.create(p, r.title());
  }

  public record ProjectChange(String action, String title) {}

  @PostMapping("/api/studio/project-state")
  @ResponseBody
  public Object projectState(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody ProjectChange r) {
    return projects.change(p, r.action(), r.title());
  }

  @GetMapping("/api/studio/users")
  @ResponseBody
  public Object findUsers(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String q) {
    return projects.searchUsers(p, q);
  }

  @GetMapping("/api/studio/invitations")
  @ResponseBody
  public Object ownInvitations(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return projects.invitations(p);
  }

  @PostMapping("/api/studio/invitations")
  @ResponseBody
  public Object inviteMember(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody Member r) {
    return projects.invite(p, r.userId(), r.permission());
  }

  public record InvitationAnswer(boolean accept) {}

  @PostMapping("/api/studio/invitations/answer")
  @ResponseBody
  public Object answerInvitation(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody InvitationAnswer r) {
    return projects.answerInvitation(p, r.accept());
  }

  @GetMapping("/api/studio/notifications")
  @ResponseBody
  public Object readNotifications(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(required = false) Long beforeId) {
    return projects.notifications(p, beforeId);
  }

  public record NotificationRead(long throughId) {}

  @PostMapping("/api/studio/notifications/read")
  @ResponseBody
  public Object readNotificationMark(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody NotificationRead r) {
    return projects.markNotifications(p, r.throughId());
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

  public record WithdrawApproval(boolean confirmed, String expectedApprovedId, String note) {}

  @PostMapping("/api/studio/revisions/{id}/approval-withdrawals")
  @ResponseBody
  public Object withdrawApproval(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody WithdrawApproval request) {
    return service.withdrawApproval(
        p, id, request.confirmed(), request.expectedApprovedId(), request.note());
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
  public Object files(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(defaultValue = "200") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return service.files(p, limit, offset);
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

  /** 한 번에 보내는 최대 구간. 브라우저 플레이어는 이어서 다음 구간을 요청한다. */
  static final long STREAM_CHUNK = 4L * 1024 * 1024;

  private final java.util.concurrent.Semaphore mediaStreams = new java.util.concurrent.Semaphore(6);

  /**
   * 영상·음악 원본을 HTTP Range로 재생한다(100 MiB 원본도 메모리에 올리지 않음). 권한·감사는 기존 파일 조회와 같고 원본은
   * 바뀌지 않는다. 내려받기는 기존 /content를 쓴다.
   */
  @GetMapping("/api/studio/files/{id}/stream")
  public void stream(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
      jakarta.servlet.http.HttpServletResponse response)
      throws IOException {
    var f = service.file(p, id);
    String mime = StudioPreview.MEDIA.get((String) f.get("extension"));
    if (mime == null || mime.startsWith("image/"))
      throw StudioService.error(415, "영상·음악 파일만 재생할 수 있습니다.");
    long size = ((Number) f.get("size_bytes")).longValue();
    long[] window = rangeWindow(range, size);
    if (window == null) {
      response.setStatus(416);
      response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
      return;
    }
    if (!mediaStreams.tryAcquire())
      throw StudioService.error(429, "재생 요청이 많습니다. 잠시 후 다시 시도하세요.");
    try (var in = service.openRange(p, id, window[0], window[1])) {
      long length = window[1] - window[0] + 1;
      response.setStatus(range == null ? 200 : 206);
      response.setContentType(mime);
      response.setContentLengthLong(length);
      response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
      if (range != null)
        response.setHeader(
            HttpHeaders.CONTENT_RANGE, "bytes " + window[0] + "-" + window[1] + "/" + size);
      response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
      response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
      response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
      var out = response.getOutputStream();
      byte[] buffer = new byte[65536];
      long remaining = length;
      while (remaining > 0) {
        int n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
        if (n < 0) break;
        out.write(buffer, 0, n);
        remaining -= n;
      }
      response.flushBuffer();
    } finally {
      mediaStreams.release();
    }
  }

  /**
   * {@code bytes=a-b}, {@code bytes=a-}, {@code bytes=-n} 한 구간만 지원한다. 헤더가 없으면 전체(200, 스트리밍).
   * 범위를 벗어나면 null(416).
   */
  static long[] rangeWindow(String range, long size) {
    if (size <= 0) return null;
    if (range == null) return new long[] {0, size - 1};
    var m = java.util.regex.Pattern.compile("bytes=(\\d*)-(\\d*)").matcher(range.trim());
    if (!m.matches() || (m.group(1).isEmpty() && m.group(2).isEmpty())) return null;
    long start, end;
    try {
      if (m.group(1).isEmpty()) {
        long suffix = Long.parseLong(m.group(2));
        if (suffix <= 0) return null;
        start = Math.max(0, size - suffix);
        end = size - 1;
      } else {
        start = Long.parseLong(m.group(1));
        end = m.group(2).isEmpty() ? size - 1 : Math.min(Long.parseLong(m.group(2)), size - 1);
      }
    } catch (NumberFormatException e) {
      return null;
    }
    if (start >= size || end < start) return null;
    return new long[] {start, Math.min(end, start + STREAM_CHUNK - 1)};
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

  @GetMapping("/api/studio/files/{id}/entries")
  @ResponseBody
  public Object entries(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id)
      throws IOException {
    return previews.entries(p, id);
  }

  @GetMapping("/api/studio/files/{id}/preview")
  public ResponseEntity<byte[]> preview(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestParam(required = false) String entry,
      @RequestParam(required = false) Integer page)
      throws IOException {
    if (page != null) {
      var pdf = previews.pdfPage(p, id, entry, page);
      return ResponseEntity.ok()
          .contentType(MediaType.IMAGE_PNG)
          .header("Cache-Control", "private, no-store")
          .header("X-Content-Type-Options", "nosniff")
          .header("X-PDF-Pages", Integer.toString(pdf.pages()))
          .body(pdf.bytes());
    }
    var view = previews.view(p, id, entry);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(view.mime()))
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'")
        .header("Content-Disposition", "inline")
        .body(view.bytes());
  }

  @GetMapping("/api/studio/files/{id}/document")
  @ResponseBody
  public Object document(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "20000") int limit)
      throws IOException {
    return previews.document(p, id, offset, limit);
  }

  @GetMapping("/api/studio/files/{id}/mcp-image")
  public ResponseEntity<byte[]> mcpImage(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id)
      throws IOException {
    var source = service.file(p, id);
    var bytes = previews.aiImage(p, id);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-Studio-Source-Sha256", (String) source.get("sha256"))
        .header(
            "X-Studio-Derivative-Sha256",
            HexFormat.of().formatHex(StudioService.digest().digest(bytes)))
        .body(bytes);
  }

  @GetMapping("/api/studio/source-import")
  @ResponseBody
  public Object sourceStatus(@AuthenticationPrincipal AuthenticatedPrincipal p) throws IOException {
    return importer.status(p);
  }

  @PostMapping("/api/studio/source-import")
  @ResponseBody
  public Object sourceImport(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody StudioProductionImporter.Request request)
      throws IOException {
    return importer.register(p, request);
  }

  @GetMapping("/api/studio/memory")
  @ResponseBody
  public Object readMemory(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return collaboration.memory(p);
  }

  @PostMapping("/api/studio/memory")
  @ResponseBody
  public Object saveMemory(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody StudioCollaborationService.Memory memory) {
    return collaboration.saveMemory(p, memory);
  }

  @GetMapping("/api/studio/revisions/{id}/comments")
  @ResponseBody
  public Object comments(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestParam(required = false) Long beforeId) {
    return collaboration.comments(p, id, beforeId);
  }

  @PostMapping("/api/studio/revisions/{id}/comments")
  @ResponseBody
  public void comment(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody StudioCollaborationService.Comment comment) {
    collaboration.comment(p, id, comment);
  }

  @GetMapping("/api/studio/pending")
  @ResponseBody
  public Object pending(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return collaboration.pending(p, limit, offset);
  }

  @GetMapping("/api/studio/assets")
  @ResponseBody
  public Object assets(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(required = false) Integer episode,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String workId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return ai.listAssets(p, episode, kind, q, workId, limit, offset);
  }

  @GetMapping("/api/studio/assets/{workId}/versions")
  @ResponseBody
  public Object assetVersions(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String workId) {
    return ai.versions(p, workId);
  }

  @GetMapping("/api/studio/reviews")
  @ResponseBody
  public Object reviews(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(required = false) String workId,
      @RequestParam(required = false) String revisionId) {
    return ai.reviews(p, workId, revisionId);
  }

  public record ReviewReport(
      String revisionId,
      String verdict,
      String summary,
      List<StudioAiService.Issue> issues,
      String idempotencyKey) {}

  @PostMapping("/api/studio/reviews")
  @ResponseBody
  public Object submitReview(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody ReviewReport r) {
    return ai.submitReview(
        p,
        StudioAiService.Connector.HUMAN,
        r.revisionId(),
        r.verdict(),
        r.summary(),
        r.issues(),
        r.idempotencyKey());
  }

  @GetMapping("/api/studio/tasks")
  @ResponseBody
  public Object tasks(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String assignee,
      @RequestParam(required = false) String workId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return ai.tasks(p, status, assignee, workId, limit, offset);
  }

  public record NewTask(
      String workId,
      String revisionId,
      String reviewId,
      String title,
      String instructions,
      String assignee,
      String idempotencyKey) {}

  @PostMapping("/api/studio/tasks")
  @ResponseBody
  public Object createTask(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody NewTask r) {
    return ai.createTask(
        p,
        StudioAiService.Connector.HUMAN,
        r.workId(),
        r.revisionId(),
        r.reviewId(),
        r.title(),
        r.instructions(),
        r.assignee(),
        r.idempotencyKey());
  }

  public record TaskStatus(String status, String resultRevisionId, String note) {}

  @PostMapping("/api/studio/tasks/{id}/status")
  @ResponseBody
  public Object taskStatus(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody TaskStatus r) {
    return ai.updateTask(
        p, StudioAiService.Connector.HUMAN, id, r.status(), r.resultRevisionId(), r.note());
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
