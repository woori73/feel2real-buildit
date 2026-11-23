package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EasyTuitionPaymentRepository extends JpaRepository<EasyTuitionPayment, Long> {
  List<EasyTuitionPayment> findByStudent_StudentIdOrderByTargetMonthDesc(Long studentId);

  List<EasyTuitionPayment> findByBusiness_BusinessId(Long businessId);

  boolean existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(
      Long studentId, Long businessId, String targetMonth);

  /** 입금·결제 확인분을 더할 때 같은 수납을 동시에 고치지 않도록 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EasyTuitionPayment> findLockedByPaymentId(Long paymentId);

  List<EasyTuitionPayment> findByTargetMonthOrderByStudent_NameAsc(String targetMonth);
}
