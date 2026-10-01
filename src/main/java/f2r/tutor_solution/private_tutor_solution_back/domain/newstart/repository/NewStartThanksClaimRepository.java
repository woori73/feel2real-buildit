package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksClaim;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartThanksClaimRepository extends JpaRepository<NewStartThanksClaim, Long> {

  List<NewStartThanksClaim> findByUserIdOrderByClaimIdDesc(Long userId);

  Optional<NewStartThanksClaim> findByUserIdAndRosterId(Long userId, Long rosterId);

  Optional<NewStartThanksClaim> findFirstByUserIdAndStatus(Long userId, String status);

  List<NewStartThanksClaim> findByRosterIdAndStatus(Long rosterId, String status);

  List<NewStartThanksClaim> findByStatusOrderByClaimIdAsc(String status);

  /** 반려 남용(전수 대입) 방지용: 최근 N시간 내 반려 건수. */
  long countByUserIdAndStatusAndDecidedAtAfter(Long userId, String status, LocalDateTime after);
}
