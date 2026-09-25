package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCutover;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCutoverRepository extends JpaRepository<EasyCutover, Long> {
  Optional<EasyCutover> findByBusiness_BusinessId(Long businessId);
}
