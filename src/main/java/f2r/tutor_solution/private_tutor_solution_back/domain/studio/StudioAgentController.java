package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 브라우저 없는 AI 작업 세션(클라우드 Claude 등)이 제작소 원본을 내려받고 개선 파일을 새 버전으로 올리는 경로.
 *
 * <p>MCP 도구는 파일을 base64 인자로만 주고받아 큰 이미지·영상을 사실상 옮기지 못한다. 이 경로는 환경변수
 * {@code STUDIO_AGENT_TOKEN}과 일치하는 {@code Authorization: Bearer} 헤더로 두 가지만 허용한다.
 *
 * <ul>
 *   <li>원본 내려받기 {@code GET /api/studio-agent/files/{id}/content}
 *   <li>새 버전 등록 {@code POST /api/studio-agent/files} — upload_studio_asset·/mcp/studio/files와 같은 서비스,
 *       항상 AI_PROPOSAL·REVIEW. 승인·삭제·공개는 이 경로에 없다.
 * </ul>
 *
 * <p>토큰이 비어 있으면 404(경로 없음과 같음). 토큰은 상수 시간 비교하고 로그에 남기지 않는다. 요청은 운영 계정
 * {@code app.studio.agent.login-id}(기본 modu)로 처리되며, 제작소 가드·프로젝트 권한 검사를 그대로 받는다.
 */
@RestController
public class StudioAgentController {
  public static final String CLIENT = "studio-agent-token";

  private final StudioService studio;
  private final StudioAiService studioAi;
  private final UserRepository users;
  private final String token;
  private final String loginId;
  private final Semaphore originalReads = new Semaphore(2);

  @Autowired
  public StudioAgentController(
      StudioService studio,
      StudioAiService studioAi,
      UserRepository users,
      @Value("${STUDIO_AGENT_TOKEN:}") String token,
      @Value("${app.studio.agent.login-id:modu}") String loginId) {
    this.studio = studio;
    this.studioAi = studioAi;
    this.users = users;
    this.token = token;
    this.loginId = loginId;
  }

  @GetMapping("/api/studio-agent/files/{id}/content")
  public void content(HttpServletRequest http, @PathVariable String id, HttpServletResponse response)
      throws IOException {
    var p = authenticate(http);
    var f = studio.file(p, id);
    if (!originalReads.tryAcquire())
      throw StudioService.error(429, "다른 원본 내려받기가 진행 중입니다. 잠시 후 다시 시도하세요.");
    try {
      byte[] bytes = studio.bytes(p, id);
      response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
      response.setContentLengthLong(bytes.length);
      response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
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

  @PostMapping(value = "/api/studio-agent/files", consumes = "multipart/form-data")
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
      throws IOException {
    var p = authenticate(http);
    if (projectId != null && !projectId.isBlank()) {
      if (!projectId.matches("[A-Za-z0-9_-]{1,64}"))
        throw StudioService.error(400, "프로젝트 식별자가 올바르지 않습니다.");
      http.setAttribute(StudioService.MCP_PROJECT_ATTRIBUTE, projectId);
    }
    if (file.isEmpty() || file.getSize() > StudioService.MAX_FILE)
      throw StudioService.error(413, "파일은 1바이트~100 MiB여야 합니다.");
    try (var in = file.getInputStream()) {
      var result =
          studioAi.uploadAsset(
              p,
              new StudioAiService.Connector(CLIENT, CLIENT, modelReported),
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
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
  }

  /** 토큰이 맞으면 운영 계정 principal을 돌려준다. 미설정 404, 불일치 401. */
  AuthenticatedPrincipal authenticate(HttpServletRequest http) {
    if (token == null || token.isBlank()) throw StudioService.error(404, "Not Found");
    String header = http.getHeader(HttpHeaders.AUTHORIZATION);
    String presented =
        header != null && header.startsWith("Bearer ") && header.length() <= 300
            ? header.substring(7).trim()
            : "";
    if (!MessageDigest.isEqual(
        token.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8)))
      throw StudioService.error(401, "invalid_token");
    var u =
        users
            .findByLoginId(loginId)
            .orElseThrow(() -> StudioService.error(503, "제작소 작업 계정이 없습니다."));
    return new AuthenticatedPrincipal(u.getUserId(), u.getLoginId(), u.getRole().name());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> error(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("error", e.getReason() == null ? "invalid_request" : e.getReason()));
  }
}
