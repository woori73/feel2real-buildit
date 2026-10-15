package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyTuitionPaymentRepository extends JpaRepository<EasyTuitionPayment, Long> {
  List<EasyTuitionPayment> findByStudent_StudentIdOrderByTargetMonthDesc(Long studentId);

  List<EasyTuitionPayment> findByBusiness_BusinessId(Long businessId);

  boolean existsByStudent_StudentIdAndBusiness_BusinessIdAndTargetMonth(
      Long studentId, Long businessId, String targetMonth);
}
