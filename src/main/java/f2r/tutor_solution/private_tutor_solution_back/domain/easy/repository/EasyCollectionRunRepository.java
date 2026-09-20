package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCollectionRunRepository extends JpaRepository<EasyCollectionRun, Long> {

  List<EasyCollectionRun> findAllByBusiness_BusinessIdOrderByRunIdDesc(Long businessId);

  List<EasyCollectionRun> findAllByOrderByRunIdDesc();
}
