package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksVerification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartThanksVerificationRepository extends JpaRepository<NewStartThanksVerification, Long> {

  /** 최근 N시간 단순 실패 건수(반복 실패 승격 판단). */
  long countByUserIdAndOutcomeAndCreatedAtAfter(Long userId, String outcome, LocalDateTime after);

  /** 이 회원에게 아직 운영자 처리 대기 중인 예외가 있는지(있으면 새 시도·중복 알림을 만들지 않는다). */
  Optional<NewStartThanksVerification> findFirstByUserIdAndOutcomeAndStatus(Long userId, String outcome, String status);

  List<NewStartThanksVerification> findByOutcomeAndStatusOrderByVerificationIdAsc(String outcome, String status);
}
