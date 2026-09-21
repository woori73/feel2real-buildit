package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardSettlementAllocation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCardSettlementAllocationRepository
    extends JpaRepository<EasyCardSettlementAllocation, Long> {
  Optional<EasyCardSettlementAllocation> findBySettlement_SettlementIdAndPayable_PayableId(
      Long settlementId, Long payableId);
}
