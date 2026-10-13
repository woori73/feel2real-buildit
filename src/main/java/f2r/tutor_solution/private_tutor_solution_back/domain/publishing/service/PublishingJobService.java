package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.CreateJobRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.JobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Template;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingJobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 출판 작업 CRUD.
 *
 * <p>모든 조회·수정은 {@code userId} 소유자 검증을 통과해야 한다.
 * 실제 CLI 실행은 {@link PublishingWorker}가 담당한다 (P1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishingJobService {

  /** 원고 업로드 허용 확장자. */
  private static final Set<String> MANUSCRIPT_EXTENSIONS =
      Set.of("md", "txt", "docx", "rtf");

  /** 원고 최대 크기 (10MB). */
  private static final long MAX_MANUSCRIPT_BYTES = 10 * 1024 * 1024;

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

  /**
   * 원고 파일을 작업 디렉토리에 첨부한다 (갭-G1/G7).
   *
   * <p>Worker가 프로비저닝 후 file.place로 반입한다.
   * 허용 확장자: md/txt/docx/rtf, 최대 10MB.
   */
  public void attachManuscript(AuthenticatedPrincipal principal, Long jobId,
      MultipartFile manuscript) {
    PublishingJob job = requireOwned(principal, jobId);
    String originalName = manuscript.getOriginalFilename();
    String ext = originalName != null && originalName.contains(".")
        ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
        : "";
    if (!MANUSCRIPT_EXTENSIONS.contains(ext)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "지원하지 않는 원고 형식입니다 (md/txt/docx/rtf): " + originalName);
    }
    if (manuscript.getSize() > MAX_MANUSCRIPT_BYTES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "원고 파일이 너무 큽니다 (최대 10MB).");
    }
    try {
      Path workDir = Path.of(props.getStorageRoot(), "jobs", String.valueOf(jobId));
      Files.createDirectories(workDir);
      // 파일명 정규화 (경로 조작 방지)
      String safeName = "manuscript." + ext;
      Path dest = workDir.resolve(safeName);
      try (var in = manuscript.getInputStream()) {
        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
      }
      log.info("원고 첨부: jobId={}, file={}", jobId, safeName);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "원고 저장 실패: " + e.getMessage(), e);
    }
  }

  /** 소유자 검증. */
  @Transactional(readOnly = true)
  public PublishingJob requireOwned(AuthenticatedPrincipal principal, Long jobId) {
    return jobRepository.findByIdAndUserId(jobId, principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "작업을 찾을 수 없습니다: " + jobId));
  }
}
