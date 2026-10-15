package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionWikiMaterialLinkRepository
    extends JpaRepository<QuestionWikiMaterialLink, Long> {

  List<QuestionWikiMaterialLink> findByQuestionWiki_QuestionWikiIdOrderByLinkIdAsc(Long questionWikiId);

  Optional<QuestionWikiMaterialLink> findByQuestionWiki_QuestionWikiIdAndMaterialKindAndMaterialId(
      Long questionWikiId, QuestionWikiMaterialKind materialKind, Long materialId);
}
