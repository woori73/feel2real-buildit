package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WemadePersonRepository extends JpaRepository<WemadePerson, Long> {
    Optional<WemadePerson> findByPersonKey(String personKey);
    List<WemadePerson> findAllByOrderByPersonKeyAsc();
    boolean existsByContentHash(String contentHash);
}
