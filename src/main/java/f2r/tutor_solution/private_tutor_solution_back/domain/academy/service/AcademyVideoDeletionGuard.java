package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoAssetCleanupRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoUploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Prevents Academy FK deletion from becoming an implicit asset-cleanup mechanism. */
@Component
@RequiredArgsConstructor
public class AcademyVideoDeletionGuard {
  private final AcademyVideoRepository videoRepository;
  private final AcademyVideoUploadSessionRepository sessionRepository;
  private final AcademyVideoOptimizationJobRepository jobRepository;
  private final AcademyVideoAssetCleanupRepository cleanupRepository;

  public void requireFullyRemoved(Long academyId) {
    if (videoRepository.existsByAcademy_AcademyId(academyId)
        || sessionRepository.existsByAcademy_AcademyId(academyId)
        || jobRepository.existsByVideo_Academy_AcademyId(academyId)
        || cleanupRepository.existsByVideo_Academy_AcademyId(academyId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "영상 또는 업로드 정리가 완료된 뒤 Academy를 삭제할 수 있습니다.");
    }
  }
}
