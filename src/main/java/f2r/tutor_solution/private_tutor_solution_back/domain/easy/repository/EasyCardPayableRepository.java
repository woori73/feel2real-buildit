package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardPayable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCardPayableRepository extends JpaRepository<EasyCardPayable, Long> {
  Optional<EasyCardPayable> findByPurchaseEvent_CardEventId(Long cardEventId);
  List<EasyCardPayable> findAllByBusiness_BusinessId(Long businessId);
}
