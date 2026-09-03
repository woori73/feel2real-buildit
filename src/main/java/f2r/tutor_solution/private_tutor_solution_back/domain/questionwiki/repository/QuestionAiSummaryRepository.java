package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionAiSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionAiSummaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionAiSummaryRepository extends JpaRepository<QuestionAiSummary, Long> {

  Optional<QuestionAiSummary>
      findFirstByQuestionWiki_QuestionWikiIdAndStatusOrderByVersionDesc(
          Long questionWikiId, QuestionAiSummaryStatus status);
}
