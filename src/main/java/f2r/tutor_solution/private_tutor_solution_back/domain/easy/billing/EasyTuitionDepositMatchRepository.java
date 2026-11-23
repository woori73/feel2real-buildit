package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyTuitionDepositMatchRepository
    extends JpaRepository<EasyTuitionDepositMatch, Long> {
  List<EasyTuitionDepositMatch> findByTxnIdIn(Collection<Long> txnIds);

  List<EasyTuitionDepositMatch> findByStatusInOrderByOccurredOnDescMatchIdDesc(
      Collection<EasyTuitionDepositMatch.Status> statuses);
}
