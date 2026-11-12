package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 폐지 자료 2단계 정리 API. 조회는 프로젝트 담당자, 보관·되살림·영구 삭제는 관리자(감독)만.
 *
 * <p>AI 작업 세션은 토큰 경로로 후보 목록만 읽어 감독께 보고한다. 실행 경로는 토큰으로 열지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class StudioCleanupController {
  private final StudioCleanupService cleanup;
  private final StudioAgentController agent;

  public record Selection(List<String> revisionIds, String note) {}

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, Object>> failed(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.<String, Object>of("message", Objects.requireNonNullElse(e.getReason(), "요청을 처리할 수 없습니다.")));
  }

  @GetMapping("/api/studio/cleanup/candidates")
  public Map<String, Object> candidates(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return cleanup.candidates(p);
  }

  @GetMapping("/api/studio/cleanup/archived")
  public List<Map<String, Object>> archived(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return cleanup.archived(p);
  }

  @GetMapping("/api/studio/cleanup/log")
  public List<Map<String, Object>> log(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return cleanup.log(p);
  }

  @PostMapping("/api/studio/cleanup/archive")
  public Map<String, Object> archive(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody Selection s) {
    return cleanup.archive(p, s.revisionIds(), s.note());
  }

  public record ItemSelection(List<String> workIds, String note) {}

  /** 감독이 지정한 작업 ID 전체(최신본 포함) 보관. */
  @PostMapping("/api/studio/cleanup/archive-items")
  public Map<String, Object> archiveItems(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody ItemSelection s) {
    return cleanup.archiveItems(p, s.workIds(), s.note());
  }

  @PostMapping("/api/studio/cleanup/restore")
  public Map<String, Object> restore(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody Selection s) {
    return cleanup.restore(p, s.revisionIds(), s.note());
  }

  @PostMapping("/api/studio/cleanup/purge")
  public Map<String, Object> purge(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody Selection s) {
    return cleanup.purge(p, s.revisionIds(), s.note());
  }

  /** AI 작업 세션(토큰)용 읽기 전용 후보 목록. */
  @GetMapping("/api/studio-agent/cleanup/candidates")
  public Map<String, Object> agentCandidates(HttpServletRequest http) {
    return cleanup.candidates(agent.authenticate(http));
  }

  /** AI 작업 세션(토큰)용 읽기 전용 보관함·삭제 기록. 감독 실행 결과를 Studio 문서로 남길 때 쓴다. */
  @GetMapping("/api/studio-agent/cleanup/log")
  public List<Map<String, Object>> agentLog(HttpServletRequest http) {
    return cleanup.log(agent.authenticate(http));
  }
}
