package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CollaborationController {
  private final CollaborationService service;
  private final CollaborationRunner runner;
  private final CollaborationGithub github;
  private final StudioService studio;
  private final String origin;

  public CollaborationController(
      CollaborationService service,
      CollaborationRunner runner,
      CollaborationGithub github,
      StudioService studio,
      @Value("${app.collaboration.public-base-url:https://feel2real.co.kr}") String origin) {
    this.service = service;
    this.runner = runner;
    this.github = github;
    this.studio = studio;
    this.origin = origin;
  }

  @ModelAttribute
  void guard(HttpServletRequest request, HttpServletResponse response) {
    response.setHeader("Cache-Control", "private, no-store");
    if (!request.getMethod().equals("GET")
        && !request.getMethod().equals("HEAD")
        && !origin.equals(request.getHeader("Origin")))
      throw CollaborationService.error(403, "동일 출처의 관제실에서 요청하세요.");
    if (request.getContentLengthLong() > 65536)
      throw CollaborationService.error(413, "요청은 64 KiB 이하입니다.");
  }

  @ExceptionHandler(ResponseStatusException.class)
  @ResponseBody
  ResponseEntity<?> error(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", e.getReason() == null ? "요청을 확인하세요." : e.getReason()));
  }

  @GetMapping("/ai-collaboration")
  String page(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    return "studio/collaboration";
  }

  @GetMapping("/api/collaboration/agents")
  @ResponseBody
  Object agents(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.agents(p);
  }

  @GetMapping("/api/collaboration/tasks")
  @ResponseBody
  Object tasks(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.list(p);
  }

  @GetMapping("/api/collaboration/tasks/{id}")
  @ResponseBody
  Object detail(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id) {
    return service.detail(p, id);
  }

  @GetMapping("/api/collaboration/tasks/{id}/events")
  @ResponseBody
  Object events(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestParam(defaultValue = "0") long beforeId) {
    return service.events(p, id, beforeId);
  }

  @GetMapping("/api/collaboration/tasks/{id}/export")
  @ResponseBody
  ResponseEntity<String> export(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id) {
    var task = service.detail(p, id);
    StringBuilder md =
        new StringBuilder(
            "# "
                + task.get("title")
                + "\n\nStatus: "
                + task.get("status")
                + " · Version: "
                + task.get("version")
                + "\n\n최근 200개 이력입니다. 이전 이력은 관제실의 더 보기에서 조회하세요.\n\n"
                + task.get("instruction")
                + "\n\n");
    @SuppressWarnings("unchecked")
    var events = (java.util.List<Map<String, Object>>) task.get("events");
    var ascending = new java.util.ArrayList<>(events);
    java.util.Collections.reverse(ascending);
    for (var event : ascending)
      md.append("## ")
          .append(event.get("actor"))
          .append(" · ")
          .append(event.get("event_type"))
          .append("\n\n")
          .append(event.get("body"))
          .append("\n\n");
    return ResponseEntity.ok()
        .header(
            "Content-Disposition", "attachment; filename=collaboration-" + task.get("id") + ".md")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(
            new org.springframework.http.MediaType(
                "text", "markdown", java.nio.charset.StandardCharsets.UTF_8))
        .body(md.toString());
  }

  @PostMapping("/api/collaboration/tasks")
  @ResponseBody
  Object create(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody CollaborationService.Create r) {
    return service.create(p, r);
  }

  @PostMapping("/api/collaboration/tasks/{id}/control")
  @ResponseBody
  Object control(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody CollaborationService.Change r) {
    var result = service.change(p, id, r);
    if (result.get("status").equals("RUNNING")) runner.launch(id);
    return result;
  }

  record Message(long version, String body) {}

  @PostMapping("/api/collaboration/tasks/{id}/messages")
  @ResponseBody
  Object message(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody Message r) {
    return service.message(p, id, r.version(), r.body());
  }

  @PostMapping("/api/collaboration/tasks/{id}/artifacts")
  @ResponseBody
  Object attach(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody CollaborationService.Artifact r) {
    return service.attach(p, id, r);
  }

  record Sync(long version, int prNumber) {}

  @PostMapping("/api/collaboration/tasks/{id}/github-sync")
  @ResponseBody
  Object sync(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody Sync r) {
    var task = service.detail(p, id);
    service.owner(p, task);
    service.expected(task, r.version());
    try {
      service.githubSnapshot(p, id, r.version(), github.snapshot(r.prNumber()));
    } catch (ResponseStatusException denied) {
      throw denied;
    } catch (Exception unavailable) {
      throw CollaborationService.error(502, "GitHub 읽기 권한·연결을 확인하세요. 원시 오류는 기록하지 않습니다.");
    }
    return service.detail(p, id);
  }
}
