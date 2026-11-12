package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingAiService;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingAiService.AiPlan;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingAiService.PlannedCommand;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DesignCraft 출판 AI 수정 API (P3).
 *
 * <p>Qoder 명세 4.3 계약을 따른다. BuildIt 홈빌더와 동일한 제안→승인→적용 구조:
 * {@code /ai/plan}은 실행하지 않고 계획만 반환하고,
 * 사용자가 승인한 계획만 {@code /revisions}로 적용한다.
 */
@RestController
@RequestMapping("/api/publishing")
@RequiredArgsConstructor
public class PublishingAiController {

  private final PublishingAiService aiService;

  /** 자연어 → 명령 계획 (dry-run, 실행 안 함). */
  @PostMapping("/ai/plan")
  public ResponseEntity<AiPlan> plan(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> body) {
    Long jobId = Long.valueOf(String.valueOf(body.get("jobId")));
    String message = String.valueOf(body.get("message"));
    return ResponseEntity.ok(aiService.plan(principal, jobId, message));
  }

  /** 승인된 계획 적용 (스냅샷 후 실행). */
  @PostMapping("/jobs/{jobId}/revisions")
  public ResponseEntity<Map<String, Object>> applyRevision(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<PlannedCommand> plan = (List<PlannedCommand>) body.get("plan");
    // 원문 보호: 사용자가 명시적으로 확인한 경우에만 잠금 영역 수정을 허용
    boolean userConfirmed = Boolean.TRUE.equals(body.get("userConfirmed"));
    Long snapshotId = aiService.applyPlan(principal, jobId, plan, userConfirmed);
    return ResponseEntity.ok(Map.of(
        "jobId", jobId,
        "applied", true,
        "snapshotId", snapshotId));
  }
}
