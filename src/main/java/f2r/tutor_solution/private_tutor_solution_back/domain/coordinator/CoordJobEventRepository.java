package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoordJobEventRepository extends JpaRepository<CoordJobEvent, Long> {
  List<CoordJobEvent> findAllByJobIdOrderByAtAscEventIdAsc(String jobId);

  /** 여러 작업의 기록을 한 번에(제작 현황판). */
  List<CoordJobEvent> findAllByJobIdInOrderByAtAscEventIdAsc(Collection<String> jobIds);
}
