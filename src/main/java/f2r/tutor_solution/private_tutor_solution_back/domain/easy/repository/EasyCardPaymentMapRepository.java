package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardPaymentMap;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCardPaymentMapRepository extends JpaRepository<EasyCardPaymentMap, Long> {
  List<EasyCardPaymentMap> findAllByBusiness_BusinessId(Long businessId);

  Optional<EasyCardPaymentMap> findByBusiness_BusinessIdAndInstrument_InstrumentId(
      Long businessId, Long instrumentId);
}
