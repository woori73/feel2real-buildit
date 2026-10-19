package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarCollectionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GrammarCollectionItemRepository extends JpaRepository<GrammarCollectionItem, Long> {
  List<GrammarCollectionItem> findByCollection_CollectionIdOrderByOrderNumAsc(Long collectionId);

  Optional<GrammarCollectionItem> findByCollection_CollectionIdAndQuestion_QuestionId(Long collectionId,
      Long questionId);

  void deleteByCollection_CollectionIdAndQuestion_QuestionIdIn(Long collectionId,
      Collection<Long> questionIds);

  long countByCollection_CollectionId(Long collectionId);
}
