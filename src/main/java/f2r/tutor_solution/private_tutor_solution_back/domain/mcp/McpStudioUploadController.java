package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioAiService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 대용량 제작물(러프 영상 MP4 등, 100 MiB까지)을 AI 연결 토큰으로 등록하는 API.
 *
 * <p>MCP 도구 인자로는 8 MiB까지만 받으므로, 같은 OAuth access token(studio:read + studio:propose)으로 multipart 원본을
 * 스트리밍한다. 권한·SHA·형식·중복·새 버전 규칙은 upload_studio_asset 도구와 같은 서비스를 쓴다. 쿠키 로그인은 받지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class McpStudioUploadController {
  private final McpOAuth oauth;
  private final McpRateLimit rate;
  private final StudioAiService studioAi;

  @PostMapping(value = "/mcp/studio/files", consumes = "multipart/form-data")
  public ResponseEntity<?> upload(
      HttpServletRequest http,
      @RequestParam MultipartFile file,
      @RequestParam String workId,
      @RequestParam String versionLabel,
      @RequestParam String changeNote,
      @RequestParam(required = false) String projectId,
      @RequestParam(required = false) String expectedHeadRevisionId,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) Integer episode,
      @RequestParam(required = false) Integer cutNumber,
      @RequestParam(required = false) String taskId,
      @RequestParam(required = false) String modelReported)
      throws Exception {
    McpOAuth.Identity identity = oauth.authenticate(http.getHeader("Authorization"));
    if (!identity.scopes().contains("studio:read")
        || !identity.scopes().contains(McpOAuth.STUDIO_PROPOSE))
      throw McpOAuth.error(403, "studio_propose_scope_required");
    rate.check("write:" + identity.grantId(), 30);
    if (projectId != null && !projectId.isBlank()) {
      if (!projectId.matches("[A-Za-z0-9_-]{1,64}")) throw McpOAuth.error(400, "invalid_project_id");
      http.setAttribute(StudioService.MCP_PROJECT_ATTRIBUTE, projectId);
    }
    if (file.isEmpty() || file.getSize() > StudioService.MAX_FILE)
      throw McpOAuth.error(413, "file_size_1B_to_100MiB");
    long start = System.nanoTime();
    try (var in = file.getInputStream()) {
      var result =
          studioAi.uploadAsset(
              identity.principal(),
              new StudioAiService.Connector(
                  oauth.clientName(identity.grantId()), identity.grantId(), modelReported),
              workId,
              expectedHeadRevisionId,
              versionLabel,
              file.getOriginalFilename(),
              in,
              title,
              kind,
              episode,
              cutNumber,
              changeNote,
              taskId);
      oauth.audit(
          identity, "studio_token_upload", workId, "ALLOW", (System.nanoTime() - start) / 1000000);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    } catch (RuntimeException e) {
      oauth.audit(
          identity, "studio_token_upload", workId, "DENY", (System.nanoTime() - start) / 1000000);
      throw e;
    }
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<?> error(org.springframework.web.server.ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("error", e.getReason() == null ? "invalid_request" : e.getReason()));
  }
}
