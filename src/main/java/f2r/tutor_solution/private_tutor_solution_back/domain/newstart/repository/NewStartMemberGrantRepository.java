package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberGrant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartMemberGrantRepository extends JpaRepository<NewStartMemberGrant, Long> {

  boolean existsByUserIdAndCapability(Long userId, NewStartCapability capability);

  List<NewStartMemberGrant> findByUserId(Long userId);

  /** 목록 화면 N+1 방지 — 여러 회원 중 capability 보유자 한 번에. */
  List<NewStartMemberGrant> findByUserIdInAndCapability(Collection<Long> userIds, NewStartCapability capability);

  Optional<NewStartMemberGrant> findByUserIdAndCapability(Long userId, NewStartCapability capability);

  List<NewStartMemberGrant> findAllByOrderByGrantIdAsc();

  long deleteByUserIdAndCapability(Long userId, NewStartCapability capability);
}
