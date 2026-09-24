package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBankAccountMap;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyBankAccountMapRepository extends JpaRepository<EasyBankAccountMap, Long> {
  List<EasyBankAccountMap> findAllByBusiness_BusinessId(Long businessId);
}
