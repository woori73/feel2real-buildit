package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyJournalLineRepository extends JpaRepository<EasyJournalLine, Long> {
  List<EasyJournalLine> findByEntry_EntryIdOrderBySortOrderAsc(Long entryId);
}
