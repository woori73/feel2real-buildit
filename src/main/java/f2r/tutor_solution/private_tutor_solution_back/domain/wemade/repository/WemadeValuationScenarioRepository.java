package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeValuationScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WemadeValuationScenarioRepository extends JpaRepository<WemadeValuationScenario, Long> {
    Optional<WemadeValuationScenario> findByScenarioKey(String key);
    List<WemadeValuationScenario> findAllByOrderByScenarioKeyAsc();
}
