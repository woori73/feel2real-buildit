package f2r.tutor_solution.private_tutor_solution_back.domain.source.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {
  List<Source> findByUploadedByUserId(Long userId);

  /** 학생 대시보드용 — 한 사용자의 특정 status source들 (검증 대기 카드 등). */
  List<Source> findByUploadedByUserIdAndProcessingStatusOrderByCreatedAtDesc(
      Long userId, ProcessingStatus status);

  /** 어드민 대시보드 활동 요약용 — 학생이 업로드한 자료 개수. */
  long countByUploadedByUserId(Long userId);

  /**
   * 검증 확정 시 같은 묶음(exam_id) 일괄 status 전이용.
   * 학생이 한 묶음으로 올린 시험지 N장은 같은 exam_id를 공유하므로, 한 번의 confirm으로 전부 COMPLETED 처리한다.
   * uploadedBy로 학생을 필터링해 다른 학생의 같은 시험은 영향받지 않게 한다.
   */
  List<Source> findByExamExamIdAndUploadedByUserIdAndProcessingStatus(
      Long examId, Long userId, ProcessingStatus status);
}
