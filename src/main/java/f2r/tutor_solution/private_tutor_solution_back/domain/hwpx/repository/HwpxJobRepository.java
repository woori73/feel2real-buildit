package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob.Status;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface HwpxJobRepository extends JpaRepository<HwpxJob, Long> {

  List<HwpxJob> findByTutorIdOrderByIdDesc(Long tutorId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE HwpxJob j SET j.status = 'RUNNING' WHERE j.id = :id AND j.status = 'QUEUED'")
  int claimQueued(@org.springframework.data.repository.query.Param("id") Long id);

  /**
   * Worker 폴링용: 가장 오래된 QUEUED 작업 목록.
   * 실행 권한은 claimQueued의 조건부 UPDATE 결과가 1인 작업자에게만 있다.
   */
  @Query("SELECT j FROM HwpxJob j WHERE j.status = :status ORDER BY j.id ASC")
  List<HwpxJob> findQueued(Status status);
}
