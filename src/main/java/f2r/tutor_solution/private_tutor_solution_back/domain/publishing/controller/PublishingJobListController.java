package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.JobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingJobService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출판 작업 목록 조회 (Qoder F1 프로젝트 목록).
 *
 * <p>{@code GET /api/publishing/jobs} → {@code { jobs: [...] }}.
 * {@link PublishingJobController}와 분리된 목록 전용 컨트롤러.
 */
@RestController
@RequestMapping("/api/publishing/jobs")
@RequiredArgsConstructor
public class PublishingJobListController {

  private final PublishingJobService jobService;

  /**
   * 내 출판 작업 목록.
   */
  @GetMapping
  public Map<String, List<JobResponse>> listJobs(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Map<String, List<JobResponse>> result = new HashMap<>();
    result.put("jobs", jobService.listJobs(principal));
    return result;
  }
}
