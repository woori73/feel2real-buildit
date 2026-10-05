package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeOwnershipEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WemadeOwnershipEdgeRepository extends JpaRepository<WemadeOwnershipEdge, Long> {
    List<WemadeOwnershipEdge> findAllByOrderByParentKeyAscChildKeyAsc();
    List<WemadeOwnershipEdge> findByParentKeyOrderByChildKeyAsc(String parentKey);
    boolean existsByContentHash(String contentHash);
}
