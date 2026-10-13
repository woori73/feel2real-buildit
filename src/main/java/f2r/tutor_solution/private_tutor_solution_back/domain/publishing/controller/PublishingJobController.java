package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.CreateJobRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.JobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingJobService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DesignCraft 출판 작업 REST API (P0: CRUD).
 *
 * <p>Qoder 명세 4.1 계약을 따른다. 모든 엔드포인트는 JWT 인증이 필요하며
 * ({@code /api/**} catch-all), 작업 소유자({@code userId}) 격리를 적용한다.
 *
 * <p>P1에서 페이지 PNG·preflight, P2에서 export·산출물, P3에서 AI·버전을 추가한다.
 */
@RestController
@RequestMapping("/api/publishing/jobs")
@RequiredArgsConstructor
public class PublishingJobController {

  private final PublishingJobService jobService;

  /** 작업 생성 → 202 QUEUED. */
  @PostMapping
  public ResponseEntity<JobResponse> createJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody CreateJobRequest request) {
    return ResponseEntity.accepted().body(jobService.createJob(principal, request));
  }

  /** 내 작업 목록. */
  @GetMapping
  public ResponseEntity<List<JobResponse>> listJobs(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(jobService.listJobs(principal));
  }

  /** 작업 상태 조회 (폴링용). */
  @GetMapping("/{jobId}")
  public ResponseEntity<JobResponse> getJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    return ResponseEntity.ok(jobService.getJob(principal, jobId));
  }

  /** 작업 취소. */
  @PostMapping("/{jobId}/cancel")
  public ResponseEntity<JobResponse> cancelJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    return ResponseEntity.ok(jobService.cancelJob(principal, jobId));
  }

  /** 작업 삭제. */
  @DeleteMapping("/{jobId}")
  public ResponseEntity<Void> deleteJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    jobService.deleteJob(principal, jobId);
    return ResponseEntity.noContent().build();
  }
}
