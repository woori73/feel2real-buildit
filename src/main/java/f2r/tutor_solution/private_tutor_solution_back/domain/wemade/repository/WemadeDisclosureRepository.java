package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WemadeDisclosureRepository extends JpaRepository<WemadeDisclosure, Long> {
    boolean existsByContentHash(String hash);
        List<WemadeDisclosure> findAllByOrderByDisclosedAtDesc();
    List<WemadeDisclosure> findTop30ByOrderByDisclosedAtDesc();
}
