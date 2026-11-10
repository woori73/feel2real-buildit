package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClass;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyClassRepository extends JpaRepository<EasyClass, Long> {
  List<EasyClass> findAllByBusiness_BusinessIdOrderByStatusAscNameAsc(Long businessId);
}
