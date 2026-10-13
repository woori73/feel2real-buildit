package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.CreateJobRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.JobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Template;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingJobRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 출판 작업 CRUD.
 *
 * <p>모든 조회·수정은 {@code userId} 소유자 검증을 통과해야 한다.
 * 실제 CLI 실행은 {@link PublishingWorker}가 담당한다 (P1).
 */
@Service
@RequiredArgsConstructor
public class PublishingJobService {

  private final PublishingJobRepository jobRepository;
  private final PublishingProperties props;

  /** 작업 생성 (P0: DB 레코드만, CLI 실행은 Worker가 폴링). */
  @Transactional
  public JobResponse createJob(AuthenticatedPrincipal principal, CreateJobRequest request) {
    String title = request.title();
    if (title == null || title.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력하세요.");
    }
    Template template;
    try {
      template = Template.fromCode(request.template());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "지원하지 않는 템플릿입니다: " + request.template());
    }
    PublishingJob job = new PublishingJob(principal.userId(), title.strip(), template);
    return JobResponse.from(jobRepository.save(job));
  }

  @Transactional(readOnly = true)
  public List<JobResponse> listJobs(AuthenticatedPrincipal principal) {
    return jobRepository.findByUserIdOrderByIdDesc(principal.userId()).stream()
        .map(JobResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public JobResponse getJob(AuthenticatedPrincipal principal, Long jobId) {
    return JobResponse.from(requireOwned(principal, jobId));
  }

  /** 작업 취소 (QUEUED/RUNNING만). */
  @Transactional
  public JobResponse cancelJob(AuthenticatedPrincipal principal, Long jobId) {
    PublishingJob job = requireOwned(principal, jobId);
    if (job.getStatus() == Status.QUEUED || job.getStatus() == Status.RUNNING) {
      job.markCancelled();
    } else {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "취소할 수 없는 상태입니다: " + job.getStatus());
    }
    return JobResponse.from(job);
  }

  /** 작업 삭제 (산출물·버전은 CASCADE). */
  @Transactional
  public void deleteJob(AuthenticatedPrincipal principal, Long jobId) {
    PublishingJob job = requireOwned(principal, jobId);
    jobRepository.delete(job);
  }

  /** 소유자 검증. */
  @Transactional(readOnly = true)
  public PublishingJob requireOwned(AuthenticatedPrincipal principal, Long jobId) {
    return jobRepository.findByIdAndUserId(jobId, principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "작업을 찾을 수 없습니다: " + jobId));
  }
}
