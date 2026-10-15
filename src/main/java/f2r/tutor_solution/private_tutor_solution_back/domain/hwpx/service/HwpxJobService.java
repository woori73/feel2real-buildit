package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.CreateHwpxJobRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.HwpxCellEdit;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.HwpxJobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxArtifact;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository.HwpxArtifactRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository.HwpxJobRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * HWPX 작업 서비스.
 *
 * <p>publishing 패키지와 독립적이다. 소유자(tutorId) 격리를 적용한다.
 */
@Service
@RequiredArgsConstructor
public class HwpxJobService {

  private final HwpxJobRepository jobRepository;
  private final HwpxArtifactRepository artifactRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public HwpxJobResponse createJob(AuthenticatedPrincipal principal,
      CreateHwpxJobRequest request, String sourceFormat) {
    if (request.getEdits() == null || request.getEdits().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "edits가 비어 있습니다");
    }
    String editsJson;
    try {
      editsJson = objectMapper.writeValueAsString(
          java.util.Map.of("edits", request.getEdits()));
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "edits 직렬화 실패");
    }
    HwpxJob job = new HwpxJob(principal.userId(), request.getTitle(), sourceFormat, editsJson);
    return new HwpxJobResponse(jobRepository.save(job));
  }

  @Transactional(readOnly = true)
  public List<HwpxJobResponse> listJobs(AuthenticatedPrincipal principal) {
    return jobRepository.findByTutorIdOrderByIdDesc(principal.userId()).stream()
        .map(HwpxJobResponse::new)
        .toList();
  }

  @Transactional(readOnly = true)
  public HwpxJobResponse getJob(AuthenticatedPrincipal principal, Long jobId) {
    return new HwpxJobResponse(getOwnedJob(principal, jobId));
  }

  @Transactional
  public HwpxJobResponse cancelJob(AuthenticatedPrincipal principal, Long jobId) {
    HwpxJob job = getOwnedJob(principal, jobId);
    if (job.getStatus() == Status.QUEUED || job.getStatus() == Status.RUNNING) {
      job.markCancelled();
    }
    return new HwpxJobResponse(job);
  }

  @Transactional
  public void deleteJob(AuthenticatedPrincipal principal, Long jobId) {
    HwpxJob job = getOwnedJob(principal, jobId);
    jobRepository.delete(job);
  }

  /**
   * 업로드 파일 저장 후 입력 파일 경로를 작업에 기록한다.
   * Worker가 실제 파일을 읽을 수 있도록 Controller에서 호출한다.
   */
  @Transactional
  public void setInputFile(AuthenticatedPrincipal principal, Long jobId, String inputFile) {
    HwpxJob job = getOwnedJob(principal, jobId);
    job.setInputFile(inputFile);
  }

  @Transactional(readOnly = true)
  public HwpxArtifact getArtifact(AuthenticatedPrincipal principal, Long jobId) {
    getOwnedJob(principal, jobId); // 소유자 확인
    return artifactRepository.findFirstByJobIdOrderByIdDesc(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "산출물 없음"));
  }

  private HwpxJob getOwnedJob(AuthenticatedPrincipal principal, Long jobId) {
    HwpxJob job = jobRepository.findById(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "작업 없음"));
    if (!job.getTutorId().equals(principal.userId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한 없음");
    }
    return job;
  }
}
