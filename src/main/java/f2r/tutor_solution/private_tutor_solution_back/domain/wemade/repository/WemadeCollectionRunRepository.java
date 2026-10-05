package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeCollectionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WemadeCollectionRunRepository extends JpaRepository<WemadeCollectionRun, Long> {
    List<WemadeCollectionRun> findTop20ByOrderByStartedAtDesc();
}
