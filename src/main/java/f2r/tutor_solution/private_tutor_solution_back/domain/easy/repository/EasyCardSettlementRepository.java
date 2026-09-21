package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardSettlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCardSettlementRepository extends JpaRepository<EasyCardSettlement, Long> {
  Optional<EasyCardSettlement> findByBusiness_BusinessIdAndFingerprint(Long businessId, String fingerprint);
}
