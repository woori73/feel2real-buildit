package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.VersionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingVersionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 버전(스냅샷) 관리 서비스 (P3).
 *
 * <p>복원 시 현재 상태를 먼저 스냅샷으로 보존하므로 "되돌리기를 되돌릴 수 있다".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishingVersionService {

  private final PublishingJobService jobService;
  private final PublishingEditService editService;
  private final PublishingVersionRepository versionRepository;

  @Transactional(readOnly = true)
  public List<VersionResponse> listVersions(AuthenticatedPrincipal principal, Long jobId) {
    jobService.requireOwned(principal, jobId); // 소유자 검증
    return versionRepository.findByJobIdOrderByIdDesc(jobId).stream()
        .map(VersionResponse::from)
        .toList();
  }

  /**
   * 특정 버전으로 복원한다.
   *
   * <p>복원 전 현재 상태를 스냅샷으로 보존한다.
   */
  @Transactional
  public void restoreVersion(AuthenticatedPrincipal principal, Long jobId, Long versionId) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    PublishingVersion version = versionRepository.findById(versionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "버전을 찾을 수 없습니다: " + versionId));
    if (!version.getJobId().equals(jobId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "버전을 찾을 수 없습니다.");
    }
    // 복원 전 현재 상태 보존
    editService.createSnapshot(job, "복원 전 (버전 " + versionId + "로 복원)");
    try {
      Path src = Path.of(version.getDesigncraftFile());
      Path dest = Path.of(job.getDesigncraftFile());
      // 경로 격리 검증: 작업 디렉토리 내부인지 확인
      Path workDir = Path.of(job.getWorkDir()).toAbsolutePath().normalize();
      if (!src.toAbsolutePath().normalize().startsWith(workDir)
          || !dest.toAbsolutePath().normalize().startsWith(workDir)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "작업 디렉토리 외부 파일에는 접근할 수 없습니다.");
      }
      Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
      log.info("버전 복원: jobId={}, versionId={}", jobId, versionId);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "버전 복원 실패: " + e.getMessage(), e);
    }
  }
}
