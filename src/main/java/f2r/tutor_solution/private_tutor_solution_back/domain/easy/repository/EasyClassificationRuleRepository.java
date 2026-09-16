package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClassificationRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyClassificationRuleRepository extends JpaRepository<EasyClassificationRule, Long> {
  List<EasyClassificationRule> findAllByActiveTrueOrderByPriorityAsc();
}
