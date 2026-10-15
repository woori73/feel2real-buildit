package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeCatalystEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WemadeCatalystEventRepository extends JpaRepository<WemadeCatalystEvent, Long> {
    boolean existsByContentHash(String contentHash);
    java.util.List<WemadeCatalystEvent> findAllByOrderByCatalystKeyAsc();
}
