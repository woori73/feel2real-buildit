package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.CreateJobRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.JobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingJobService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * DesignCraft 출판 작업 REST API.
 *
 * <p>Qoder API_INTEGRATION.md 계약을 따른다. 모든 엔드포인트는 JWT 인증이 필요하며
 * ({@code /api/**} catch-all), 작업 소유자({@code userId}) 격리를 적용한다.
 */
@RestController
@RequestMapping("/api/publishing/jobs")
@RequiredArgsConstructor
public class PublishingJobController {

  private final PublishingJobService jobService;

  /**
   * 작업 목록 → { jobs: [...] } (Qoder F1 프로젝트 목록).
   */
  @GetMapping
  public Map<String, List<JobResponse>> listJobs(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    List<JobResponse> jobs = jobService.listJobs(principal);
    Map<String, List<JobResponse>> result = new java.util.HashMap<>();
    result.put("jobs", jobs);
    return result;
  }

  /**
   * 작업 생성 → 202 QUEUED (JSON).
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JobResponse> createJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody CreateJobRequest request) {
    return ResponseEntity.accepted().body(jobService.createJob(principal, request));
  }

  /**
   * 작업 생성 → 202 QUEUED (multipart, 원고 파일 포함 가능).
   *
   * <p>Qoder 갭-G1: {@code template}, {@code title}, {@code manuscript?} multipart.
   * 원고는 작업 생성 후 Worker가 file.place로 반입한다.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<JobResponse> createJobMultipart(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestParam("template") String template,
      @RequestParam("title") String title,
      @RequestParam(value = "manuscript", required = false) MultipartFile manuscript) {
    JobResponse job = jobService.createJob(
        principal, new CreateJobRequest(title, template));
    if (manuscript != null && !manuscript.isEmpty()) {
      jobService.attachManuscript(principal, job.jobId(), manuscript);
    }
    return ResponseEntity.accepted().body(jobService.getJob(principal, job.jobId()));
  }

  /** 내 작업 목록 → { jobs: [...] }. */
  @GetMapping
  public ResponseEntity<Map<String, List<JobResponse>>> listJobs(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(Map.of("jobs", jobService.listJobs(principal)));
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
