package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadePolicyTracking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WemadePolicyTrackingRepository extends JpaRepository<WemadePolicyTracking, Long> {
    boolean existsByContentHash(String contentHash);
    java.util.List<WemadePolicyTracking> findAllByOrderByPolicyKeyAsc();
}
