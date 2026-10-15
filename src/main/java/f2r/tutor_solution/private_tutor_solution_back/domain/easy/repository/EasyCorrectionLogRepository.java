package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCorrectionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCorrectionLogRepository extends JpaRepository<EasyCorrectionLog, Long> {
  List<EasyCorrectionLog> findByBusiness_BusinessIdOrderByLogIdDesc(Long businessId);

  List<EasyCorrectionLog> findAllByOrderByLogIdDesc();

  List<EasyCorrectionLog> findByTxn_TxnIdOrderByLogIdDesc(Long txnId);
}
