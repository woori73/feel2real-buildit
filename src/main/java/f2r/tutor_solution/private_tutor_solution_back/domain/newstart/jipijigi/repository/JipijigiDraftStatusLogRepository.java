package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraftStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JipijigiDraftStatusLogRepository extends JpaRepository<JipijigiDraftStatusLog, Long> {
    List<JipijigiDraftStatusLog> findByDraftIdOrderByCreatedAtAsc(Long draftId);
}
