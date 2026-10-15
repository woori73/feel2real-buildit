package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WemadeEventRepository extends JpaRepository<WemadeEvent, Long> {
    boolean existsByContentHash(String hash);
        List<WemadeEvent> findAllByOrderByEventDateDesc();
}
