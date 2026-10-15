package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorQuestionSet;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorSetQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemGeneratorSetQuestionRepository extends JpaRepository<ProblemGeneratorSetQuestion, Long> {
  List<ProblemGeneratorSetQuestion> findBySet_SetIdOrderByOrderNumAsc(Long setId);
  void deleteBySet(ProblemGeneratorQuestionSet set);
}
