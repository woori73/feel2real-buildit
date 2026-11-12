package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioAiService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
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
  /** begin_studio_upload 티켓을 담는 헤더. Authorization이 아니다(호스트가 관리하는 OAuth 토큰 없이 쓴다). */
  public static final String TICKET_HEADER = "X-Studio-Upload-Ticket";

  private final McpOAuth oauth;
  private final McpRateLimit rate;
  private final StudioAiService studioAi;
  private final McpStudioUploadTickets tickets;

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

  /**
   * begin_studio_upload로 받은 1회용 티켓 + 원본 bytes(application/octet-stream, multipart 아님)로 새 버전을 등록한다.
   *
   * <p>업로드 정보와 연결(grant)은 티켓 발급 때 정해졌고, 이 요청은 파일 내용만 보낸다. 권한·SHA·형식·중복·새 버전 규칙은
   * upload_studio_asset·/mcp/studio/files와 같은 서비스를 쓴다. 100 MiB를 넘으면 읽기를 멈추고 거부한다.
   */
  @PostMapping("/mcp/studio/uploads")
  public ResponseEntity<?> ticketUpload(HttpServletRequest http) throws Exception {
    McpStudioUploadTickets.Pending ticket = tickets.redeem(http.getHeader(TICKET_HEADER));
    if (ticket == null) throw McpOAuth.error(401, "invalid_or_expired_upload_ticket");
    McpOAuth.Identity identity = ticket.identity();
    var a = ticket.args();
    rate.check("write:" + identity.grantId(), 30);
    if (a.projectId() != null) http.setAttribute(StudioService.MCP_PROJECT_ATTRIBUTE, a.projectId());
    long declared = http.getContentLengthLong();
    if (declared == 0 || declared > StudioService.MAX_FILE) throw McpOAuth.error(413, "file_size_1B_to_100MiB");
    long start = System.nanoTime();
    try (var in = new Limited(http.getInputStream(), StudioService.MAX_FILE)) {
      var result =
          studioAi.uploadAsset(
              identity.principal(),
              new StudioAiService.Connector(ticket.clientName(), identity.grantId(), a.modelReported()),
              a.workId(),
              a.expectedHeadRevisionId(),
              a.versionLabel(),
              a.filename(),
              in,
              a.title(),
              a.kind(),
              a.episode(),
              a.cutNumber(),
              a.changeNote(),
              a.taskId());
      oauth.audit(identity, "studio_ticket_upload", a.workId(), "ALLOW", (System.nanoTime() - start) / 1000000);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    } catch (RuntimeException | IOException e) {
      oauth.audit(identity, "studio_ticket_upload", a.workId(), "DENY", (System.nanoTime() - start) / 1000000);
      for (Throwable c = e; c != null; c = c.getCause())
        if (c instanceof Limited.TooLarge) throw McpOAuth.error(413, "file_size_1B_to_100MiB");
      throw e;
    }
  }

  /** 선언 길이가 없거나 틀려도 상한을 넘는 순간 읽기를 멈춘다. */
  static final class Limited extends FilterInputStream {
    static final class TooLarge extends IOException {
      TooLarge() {
        super("upload larger than limit");
      }
    }

    private final long max;
    private long total;

    Limited(InputStream in, long max) {
      super(in);
      this.max = max;
    }

    @Override
    public int read() throws IOException {
      int b = super.read();
      if (b != -1 && ++total > max) throw new TooLarge();
      return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      int n = super.read(buf, off, len);
      if (n > 0 && (total += n) > max) throw new TooLarge();
      return n;
    }
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<?> error(org.springframework.web.server.ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("error", e.getReason() == null ? "invalid_request" : e.getReason()));
  }
}
