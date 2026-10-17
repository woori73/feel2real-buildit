package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

/**
 * 수집 승인 저장소 — P1/P3.
 */
@Repository
public interface EasyCollectionApprovalRepository
    extends JpaRepository<EasyCollectionApproval, Long> {

  Optional<EasyCollectionApproval> findByRun_RunId(Long runId);

  Optional<EasyCollectionApproval> findByIdempotencyKey(String idempotencyKey);

  List<EasyCollectionApproval> findByBusiness_BusinessIdAndStatusOrderByRequestedAtDesc(
      Long businessId, EasyCollectionApproval.ApprovalStatus status);

  /**
   * 장부 반영용 비관적 락 조회 — 동시 commit 시도를 직렬화한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EasyCollectionApproval> findWithLockByApprovalId(Long approvalId);
}
