package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeOwnershipNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WemadeOwnershipNodeRepository extends JpaRepository<WemadeOwnershipNode, Long> {
    Optional<WemadeOwnershipNode> findByNodeKey(String nodeKey);
    List<WemadeOwnershipNode> findAllByOrderByNodeKeyAsc();
    boolean existsByContentHash(String contentHash);
}
