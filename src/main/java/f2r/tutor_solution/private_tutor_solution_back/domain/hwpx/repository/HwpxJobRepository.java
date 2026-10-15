package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob.Status;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HwpxJobRepository extends JpaRepository<HwpxJob, Long> {

  List<HwpxJob> findByTutorIdOrderByIdDesc(Long tutorId);

  /**
   * Worker 폴링용: 가장 오래된 QUEUED 작업 목록.
   * 실제 락은 Worker에서 @Transactional로 처리한다.
   */
  @Query("SELECT j FROM HwpxJob j WHERE j.status = :status ORDER BY j.id ASC")
  List<HwpxJob> findQueued(Status status);
}
