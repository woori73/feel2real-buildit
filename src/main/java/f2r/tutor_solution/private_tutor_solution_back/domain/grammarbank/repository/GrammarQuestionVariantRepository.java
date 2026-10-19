package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GrammarQuestionVariantRepository extends JpaRepository<GrammarQuestionVariant, Long> {
  List<GrammarQuestionVariant> findByBaseQuestion_QuestionId(Long baseQuestionId);

  List<GrammarQuestionVariant> findByBaseQuestion_QuestionIdIn(Collection<Long> baseQuestionIds);
}
