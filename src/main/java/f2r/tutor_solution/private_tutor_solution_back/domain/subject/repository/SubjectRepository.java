package f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Integer> {
}
