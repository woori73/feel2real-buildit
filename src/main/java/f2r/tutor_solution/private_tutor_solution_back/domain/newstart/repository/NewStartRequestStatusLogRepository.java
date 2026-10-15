package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestStatusLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 상태 이동 이력 — 상세 화면에서 오래된 순으로 전체 흐름을 보여준다. */
public interface NewStartRequestStatusLogRepository extends JpaRepository<NewStartRequestStatusLog, Long> {

  List<NewStartRequestStatusLog> findByRequestIdOrderByLogIdAsc(Long requestId);
}
