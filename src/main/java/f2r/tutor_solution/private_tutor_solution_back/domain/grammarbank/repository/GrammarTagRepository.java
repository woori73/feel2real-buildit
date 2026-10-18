package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrammarTagRepository extends JpaRepository<GrammarTag, Long> {
  List<GrammarTag> findAllByOrderBySortOrderAsc();

  Optional<GrammarTag> findByNameKo(String nameKo);
}
