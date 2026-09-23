package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyLocalCollectorRepository extends JpaRepository<EasyLocalCollector, Long> {
  Optional<EasyLocalCollector> findByTokenHash(String tokenHash);
  List<EasyLocalCollector> findAllByBusiness_BusinessIdOrderByCollectorIdDesc(Long businessId);
}
