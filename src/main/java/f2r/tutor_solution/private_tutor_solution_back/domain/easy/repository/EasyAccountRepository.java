package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyAccountRepository extends JpaRepository<EasyAccount, Long> {
  java.util.Optional<EasyAccount> findByCode(String code);

  List<EasyAccount> findAllByActiveTrueOrderBySortOrderAsc();

  List<EasyAccount> findAllByActiveTrueAndDirectionOrderBySortOrderAsc(EasyAccountDirection direction);
}
