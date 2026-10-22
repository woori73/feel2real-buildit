package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Status;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface PublishingJobRepository extends JpaRepository<PublishingJob, Long> {

  List<PublishingJob> findByUserIdOrderByIdDesc(Long userId);

  Optional<PublishingJob> findByIdAndUserId(Long id, Long userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE PublishingJob j SET j.status = 'RUNNING' WHERE j.id = :id AND j.status = 'QUEUED'")
  int claimQueued(@org.springframework.data.repository.query.Param("id") Long id);

  /**
   * Worker 폴링용: 가장 오래된 QUEUED 작업 1건.
   * 실행 권한은 claimQueued의 조건부 UPDATE 결과가 1인 작업자에게만 있다.
   */
  @Query("SELECT j FROM PublishingJob j WHERE j.status = :status ORDER BY j.id ASC")
  List<PublishingJob> findQueued(@Param("status") Status status);
}
