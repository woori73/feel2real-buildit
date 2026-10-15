package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardBillingGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCardBillingGroupRepository extends JpaRepository<EasyCardBillingGroup, Long> {
  Optional<EasyCardBillingGroup> findByBusiness_BusinessIdAndFingerprint(Long businessId, String fingerprint);
}
