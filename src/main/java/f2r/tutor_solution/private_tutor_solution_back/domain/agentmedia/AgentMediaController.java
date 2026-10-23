package f2r.tutor_solution.private_tutor_solution_back.domain.agentmedia;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 대시보드 캐릭터 자료 제공·설치. 접근 권한은 SecurityConfig가 막는다.
 * <ul>
 *   <li>읽기(manifest·파일): ADMIN·TUTOR</li>
 *   <li>자료 묶음 설치(POST /api/dashboard/agent-media/package): ADMIN만, 같은 출처 요청만</li>
 *   <li>홈페이지 공개 자료(/agent-media/public/**): app.agent-media.public-agents에 적은 캐릭터만(기본 없음)</li>
 * </ul>
 * 영상은 Resource로 돌려주므로 브라우저 Range 요청(206)을 Spring MVC가 그대로 처리한다.
 */
@Slf4j
@RestController
public class AgentMediaController {

  static final long MAX_PACKAGE_BYTES = 64L * 1024 * 1024;

  private final AgentMediaStore store;
  private final List<String> allowedOrigins;

  public AgentMediaController(
      AgentMediaStore store,
      @Value("${buildit.unified-assistant.allowed-origins:http://localhost:8080,https://feel2real.co.kr}") List<String> allowedOrigins) {
    this.store = store;
    this.allowedOrigins = allowedOrigins;
  }

  @GetMapping("/api/dashboard/agent-media")
  public ResponseEntity<Map<String, Object>> manifest() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.manifest());
  }

  @GetMapping("/dashboard/agent-media/{agent}/{file}")
  public ResponseEntity<Resource> file(@PathVariable("agent") String agent, @PathVariable("file") String file) {
    return serve(store.find(agent, file), file, CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate());
  }

  @GetMapping("/api/agent-media/public")
  public ResponseEntity<Map<String, Object>> publicManifest() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.publicManifest());
  }

  @GetMapping("/agent-media/public/{agent}/{file}")
  public ResponseEntity<Resource> publicFile(@PathVariable("agent") String agent, @PathVariable("file") String file) {
    return serve(store.findPublic(agent, file), file, CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
  }

  @PostMapping(value = "/api/dashboard/agent-media/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> install(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      HttpServletRequest request,
      @RequestPart("file") MultipartFile file) throws IOException {
    String origin = request.getHeader("Origin");
    if (!allowedOrigins.contains(origin == null ? "" : origin)) return error(HttpStatus.FORBIDDEN, "같은 출처 요청만 허용합니다.");
    if (!store.configured()) return error(HttpStatus.SERVICE_UNAVAILABLE, "서버의 캐릭터 자료 폴더가 설정되지 않았습니다.");
    if (file.isEmpty() || file.getSize() > MAX_PACKAGE_BYTES) return error(HttpStatus.BAD_REQUEST, "64MB 이하의 ZIP 자료 묶음을 올려 주세요.");
    try (InputStream in = file.getInputStream()) {
      Map<String, Object> result = store.install(in);
      log.info("[AGENT_MEDIA] package installed by userId={} size={}", principal == null ? null : principal.userId(), file.getSize());
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    } catch (IllegalArgumentException e) {
      return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
  }

  private static ResponseEntity<Resource> serve(Optional<Path> found, String file, CacheControl cache) {
    return found
        .map(path -> ResponseEntity.ok()
            .cacheControl(cache)
            .contentType(MediaType.parseMediaType(AgentMediaStore.FILES.get(file)))
            .body((Resource) new FileSystemResource(path)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
