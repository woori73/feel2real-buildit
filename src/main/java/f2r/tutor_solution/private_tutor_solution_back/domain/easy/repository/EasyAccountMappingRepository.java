package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyAccountMappingRepository extends JpaRepository<EasyAccountMapping, Long> {
  List<EasyAccountMapping> findAllByOrderByMappingIdAsc();
}
