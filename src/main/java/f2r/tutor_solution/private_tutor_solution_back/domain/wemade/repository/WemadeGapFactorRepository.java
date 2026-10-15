package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeGapFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WemadeGapFactorRepository extends JpaRepository<WemadeGapFactor, Long> {
    Optional<WemadeGapFactor> findByFactorKey(String key);
    List<WemadeGapFactor> findAllByOrderByHypothesisNoAscFactorKeyAsc();
    boolean existsByContentHash(String contentHash);
}
