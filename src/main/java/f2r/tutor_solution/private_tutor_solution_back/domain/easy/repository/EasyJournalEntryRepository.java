package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyJournalEntryRepository extends JpaRepository<EasyJournalEntry, Long> {
  List<EasyJournalEntry> findByTxn_TxnIdAndStatusOrderByEntryIdDesc(
      Long txnId, EasyJournalStatus status);

  List<EasyJournalEntry> findByTxn_TxnIdOrderByEntryIdDesc(Long txnId);

  List<EasyJournalEntry> findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
      Long businessId, LocalDate from, LocalDate to, EasyJournalStatus status);

  List<EasyJournalEntry> findByOccurredOnBetweenAndStatusOrderByEntryIdAsc(
      LocalDate from, LocalDate to, EasyJournalStatus status);
}
