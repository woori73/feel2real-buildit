package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyBusinessRepository extends JpaRepository<EasyBusiness, Long> {
  List<EasyBusiness> findAllByActiveTrueOrderBySortOrderAsc();
}
