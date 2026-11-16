package f2r.tutor_solution.private_tutor_solution_back.domain.user.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.TemporaryPasswordIssue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemporaryPasswordIssueRepository extends JpaRepository<TemporaryPasswordIssue, Long> {
}
