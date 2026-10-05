package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePriceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WemadePriceEventRepository extends JpaRepository<WemadePriceEvent, Long> {
    List<WemadePriceEvent> findAllByOrderByEventDateAsc();
    boolean existsByContentHash(String contentHash);
}
