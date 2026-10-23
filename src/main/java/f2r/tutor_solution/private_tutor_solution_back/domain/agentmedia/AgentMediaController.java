package f2r.tutor_solution.private_tutor_solution_back.domain.agentmedia;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 캐릭터 자료 제공. 접근 권한(ADMIN·TUTOR)은 SecurityConfig가 막는다.
 * 영상은 Resource로 돌려주므로 브라우저 Range 요청(206)을 Spring MVC가 그대로 처리한다.
 */
@RestController
@RequiredArgsConstructor
public class AgentMediaController {

  private final AgentMediaStore store;

  @GetMapping("/api/dashboard/agent-media")
  public ResponseEntity<Map<String, Object>> manifest() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.manifest());
  }

  @GetMapping("/dashboard/agent-media/{agent}/{file}")
  public ResponseEntity<Resource> file(@PathVariable("agent") String agent, @PathVariable("file") String file) {
    return store.find(agent, file)
        .map(path -> ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
            .contentType(MediaType.parseMediaType(AgentMediaStore.FILES.get(file)))
            .body((Resource) new FileSystemResource(path)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

}
