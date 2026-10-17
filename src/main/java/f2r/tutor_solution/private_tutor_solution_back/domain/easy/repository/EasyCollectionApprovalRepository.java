package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 수집 승인 저장소 — P1.
 */
@Repository
public interface EasyCollectionApprovalRepository
    extends JpaRepository<EasyCollectionApproval, Long> {

  Optional<EasyCollectionApproval> findByRun_RunId(Long runId);

  Optional<EasyCollectionApproval> findByIdempotencyKey(String idempotencyKey);

  List<EasyCollectionApproval> findByBusiness_BusinessIdAndStatusOrderByRequestedAtDesc(
      Long businessId, EasyCollectionApproval.ApprovalStatus status);
}
