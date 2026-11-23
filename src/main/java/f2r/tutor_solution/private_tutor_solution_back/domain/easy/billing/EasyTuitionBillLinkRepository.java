package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyTuitionBillLinkRepository extends JpaRepository<EasyTuitionBillLink, Long> {
  Optional<EasyTuitionBillLink> findByTokenHash(String tokenHash);

  List<EasyTuitionBillLink> findByPayment_PaymentIdAndStatus(
      Long paymentId, EasyTuitionBillLink.Status status);

  List<EasyTuitionBillLink> findByPayment_PaymentIdInOrderByLinkIdDesc(Collection<Long> paymentIds);

  List<EasyTuitionBillLink> findByPayment_Business_BusinessIdAndStatus(
      Long businessId, EasyTuitionBillLink.Status status);

  boolean existsByDepositorCodeAndStatus(String depositorCode, EasyTuitionBillLink.Status status);
}
