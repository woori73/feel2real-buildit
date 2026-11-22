package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EasyTuitionPgOrderRepository extends JpaRepository<EasyTuitionPgOrder, Long> {
  Optional<EasyTuitionPgOrder> findByOrderId(String orderId);

  /** 같은 결제 알림이 동시에 두 번 와도 한 번만 처리되도록 주문 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EasyTuitionPgOrder> findLockedByOrderId(String orderId);
}
