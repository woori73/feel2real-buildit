package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartMemberProfileRepository extends JpaRepository<NewStartMemberProfile, Long> {
}
