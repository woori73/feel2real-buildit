package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeChinaDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WemadeChinaDisclosureRepository extends JpaRepository<WemadeChinaDisclosure, Long> {
    boolean existsByContentHash(String hash);
    List<WemadeChinaDisclosure> findAllByOrderByAnnouncedAtDesc();
    List<WemadeChinaDisclosure> findTop30ByOrderByAnnouncedAtDesc();
}
