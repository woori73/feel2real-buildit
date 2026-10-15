package f2r.tutor_solution.private_tutor_solution_back.domain.question.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassageRepository extends JpaRepository<Passage, Long> {
}
