package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TpQuestionRepository extends JpaRepository<TpQuestion, Long> {

  List<TpQuestion> findByExamIdOrderByOrderNoAsc(Long examId);

  List<TpQuestion> findByExamIdAndQuestionIdInOrderByOrderNoAsc(Long examId, Collection<Long> questionIds);

  long countByExamId(Long examId);
}
