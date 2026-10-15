package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface EasyTransactionRepository extends JpaRepository<EasyTransaction, Long> {
  Optional<EasyTransaction> findByTxnIdAndBusiness_BusinessId(Long txnId, Long businessId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EasyTransaction> findLockedByTxnIdAndBusiness_BusinessId(
      Long txnId, Long businessId);

  List<EasyTransaction> findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
      Long businessId, LocalDate from, LocalDate to);

  List<EasyTransaction> findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
      LocalDate from, LocalDate to);

  List<EasyTransaction> findTop10ByBusiness_BusinessIdInOrderByOccurredOnDescTxnIdDesc(
      List<Long> businessIds);

  List<EasyTransaction> findTop10ByOrderByOccurredOnDescTxnIdDesc();

  List<EasyTransaction> findByBusiness_BusinessIdAndFingerprintIsNotNull(Long businessId);

  List<EasyTransaction> findByFingerprintIsNotNull();

  /**
   * Phase 5A.2: Find posted transactions for historical exact match recommendation.
   * Used to find consistent account usage for same merchant within a business.
   */
  List<EasyTransaction> findByBusiness_BusinessIdAndDirectionAndCounterparty(
      Long businessId, EasyTxnDirection direction, String counterparty);
}
