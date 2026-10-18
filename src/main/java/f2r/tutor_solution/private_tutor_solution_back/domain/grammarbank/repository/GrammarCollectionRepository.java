package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrammarCollectionRepository extends JpaRepository<GrammarCollection, Long> {
  List<GrammarCollection> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
}
