package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob.Status;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HwpxJobRepository extends JpaRepository<HwpxJob, Long> {

  List<HwpxJob> findByTutorIdOrderByIdDesc(Long tutorId);

  @Query(value = "SELECT * FROM hwpx_jobs WHERE status = 'QUEUED' ORDER BY id LIMIT 10 FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<HwpxJob> findQueued(Status status);
}
