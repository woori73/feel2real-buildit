package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyTransactionRepository extends JpaRepository<EasyTransaction, Long> {
  List<EasyTransaction> findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
      Long businessId, LocalDate from, LocalDate to);

  List<EasyTransaction> findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
      LocalDate from, LocalDate to);

  List<EasyTransaction> findTop10ByBusiness_BusinessIdInOrderByOccurredOnDescTxnIdDesc(
      List<Long> businessIds);

  List<EasyTransaction> findTop10ByOrderByOccurredOnDescTxnIdDesc();

  List<EasyTransaction> findByBusiness_BusinessIdAndFingerprintIsNotNull(Long businessId);

  List<EasyTransaction> findByFingerprintIsNotNull();
}
