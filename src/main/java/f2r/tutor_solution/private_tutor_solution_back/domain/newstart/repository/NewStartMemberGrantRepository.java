package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberGrant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartMemberGrantRepository extends JpaRepository<NewStartMemberGrant, Long> {

  boolean existsByUserIdAndCapability(Long userId, NewStartCapability capability);

  List<NewStartMemberGrant> findByUserId(Long userId);
}
