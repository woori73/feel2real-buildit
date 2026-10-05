package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeFactCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WemadeFactCheckRepository extends JpaRepository<WemadeFactCheck, Long> {
    Optional<WemadeFactCheck> findByClaimKey(String key);
        List<WemadeFactCheck> findAllByOrderByClaimKeyAsc();
}
