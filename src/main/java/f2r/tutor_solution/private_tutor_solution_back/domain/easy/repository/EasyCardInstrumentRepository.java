package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardInstrument;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCardInstrumentRepository extends JpaRepository<EasyCardInstrument, Long> {
  Optional<EasyCardInstrument> findByBusiness_BusinessIdAndProviderCodeAndProviderInstrumentTokenHash(
      Long businessId, String providerCode, String providerInstrumentTokenHash);
}
