package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.QuestionGrammarTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuestionGrammarTagRepository extends JpaRepository<QuestionGrammarTag, Long> {
  List<QuestionGrammarTag> findByQuestion_QuestionId(Long questionId);

  List<QuestionGrammarTag> findByQuestion_QuestionIdIn(Collection<Long> questionIds);

  void deleteByQuestion_QuestionId(Long questionId);
}
