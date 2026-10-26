package f2r.tutor_solution.private_tutor_solution_back.domain.naesin;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class NaesinProductionController {
  private final NaesinProductionService service;
  @Value("${app.studio.public-base-url:${app.mcp.public-base-url:https://feel2real.co.kr}}") private String publicBase;

  @ModelAttribute
  public void protect(HttpServletRequest request, HttpServletResponse response) {
    response.setHeader("Cache-Control", "private, no-store");
    response.setHeader("X-Robots-Tag", "noindex, nofollow");
    if (!Set.of("GET", "HEAD").contains(request.getMethod()) && !publicBase.equals(request.getHeader("Origin")))
      throw NaesinProductionService.error(403, "동일 출처에서 작업하세요.");
  }
  @ExceptionHandler(ResponseStatusException.class) @ResponseBody
  ResponseEntity<?> error(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", Objects.toString(e.getReason(), "요청 오류")));
  }
  @ExceptionHandler(IOException.class) @ResponseBody
  ResponseEntity<?> fileError(IOException e) { return ResponseEntity.unprocessableEntity().body(Map.of("message", "원본 파일을 읽을 수 없습니다.")); }

  @GetMapping("/naesin-production-studio")
  public String page(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    service.access(p); return "naesin/production";
  }
  @GetMapping("/naesin-wiki/published")
  public String wiki(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    service.published(p, 0); return "naesin/published";
  }
  @GetMapping("/api/naesin-production/projects") @ResponseBody
  public Object projects(@AuthenticationPrincipal AuthenticatedPrincipal p) { return service.list(p); }
  @PostMapping("/api/naesin-production/projects") @ResponseBody
  public Object create(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody NaesinProductionService.Project r) {
    return service.create(p, r);
  }
  @GetMapping("/api/naesin-production/state") @ResponseBody
  public Object state(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId,
      @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="0") int offset) {
    service.select(p, projectId); return service.state(p, q, offset);
  }
  @PostMapping("/api/naesin-production/project-state") @ResponseBody
  public Object change(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId,
      @RequestBody Map<String,String> r) {
    service.select(p, projectId); return service.projectChange(p, r.get("action"), r.get("title"));
  }
  @PostMapping("/api/naesin-production/files") @ResponseBody
  public Object upload(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId,
      @RequestParam MultipartFile file, @RequestParam String source, @RequestParam String rights) throws Exception {
    service.select(p, projectId); return service.upload(p, file, source, rights);
  }
  @PostMapping("/api/naesin-production/work") @ResponseBody
  public Object work(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId,
      @RequestBody NaesinProductionService.Work r) {
    service.select(p, projectId); return service.save(p, r);
  }
  @GetMapping("/api/naesin-production/revisions/{id}") @ResponseBody
  public Object detail(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId, @PathVariable String id) {
    service.select(p, projectId); return service.detail(p, id);
  }
  @PostMapping("/api/naesin-production/revisions/{id}/checks") @ResponseBody
  public Object check(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId,
      @PathVariable String id, @RequestBody NaesinProductionService.Check r) {
    service.select(p, projectId); return service.check(p, id, r);
  }
  @PostMapping("/api/naesin-production/revisions/{id}/review") @ResponseBody
  public Object review(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId,
      @PathVariable String id, @RequestBody NaesinProductionService.Decision r) {
    service.select(p, projectId); return service.review(p, id, r);
  }
  @PostMapping("/api/naesin-production/revisions/{id}/publish") @ResponseBody
  public Object publish(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId,
      @PathVariable String id, @RequestBody NaesinProductionService.Publication r) {
    service.select(p, projectId); return service.publish(p, id, r);
  }
  @PostMapping("/api/naesin-production/files/{id}/extract") @ResponseBody
  public Object extract(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId,
      @PathVariable String id, @RequestParam(defaultValue="false") boolean retry) throws IOException {
    service.select(p, projectId); return service.extract(p, id, retry);
  }
  @GetMapping("/api/naesin-production/files/{id}/download")
  public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam String projectId, @PathVariable String id) throws IOException {
    service.select(p, projectId); return attachment(service.file(p, id));
  }
  @GetMapping("/api/naesin-wiki/publications") @ResponseBody
  public Object publications(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam(defaultValue="0") int offset) {
    return service.published(p, offset);
  }
  @GetMapping("/api/naesin-wiki/publications/{id}/download")
  public ResponseEntity<byte[]> publishedFile(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable long id) throws IOException {
    return attachment(service.publishedFile(p, id));
  }
  private ResponseEntity<byte[]> attachment(Map<String,Object> f) throws IOException {
    String name = (String) f.get("filename");
    return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox")
        .header("Content-Disposition", ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
        .contentType(MediaType.parseMediaType(DocumentUploadValidator.contentType(name))).body(service.fileBytes(f));
  }
}
