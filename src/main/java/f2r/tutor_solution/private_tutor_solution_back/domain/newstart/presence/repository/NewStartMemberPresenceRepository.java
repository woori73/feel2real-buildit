package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity.NewStartMemberPresence;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartMemberPresenceRepository extends JpaRepository<NewStartMemberPresence, Long> {

  List<NewStartMemberPresence> findByLastSeenAtAfterAndHideOnlineFalseOrderByLastSeenAtDesc(
      LocalDateTime cutoff);
}
