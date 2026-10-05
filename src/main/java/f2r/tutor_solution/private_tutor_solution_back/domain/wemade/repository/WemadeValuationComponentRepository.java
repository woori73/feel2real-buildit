package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeValuationComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WemadeValuationComponentRepository extends JpaRepository<WemadeValuationComponent, Long> {
    Optional<WemadeValuationComponent> findByComponentKey(String key);
    List<WemadeValuationComponent> findAllByOrderByComponentKeyAsc();
}
