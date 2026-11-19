package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoordJobEventRepository extends JpaRepository<CoordJobEvent, Long> {
  List<CoordJobEvent> findAllByJobIdOrderByAtAscEventIdAsc(String jobId);
}
