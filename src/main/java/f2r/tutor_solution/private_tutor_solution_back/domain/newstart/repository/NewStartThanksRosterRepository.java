package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartThanksRosterRepository extends JpaRepository<NewStartThanksRoster, Long> {

  long countBySnapshotDate(LocalDate snapshotDate);

  long countByMatchStatus(String matchStatus);

  Optional<NewStartThanksRoster> findBySnapshotDateAndCafeMemberTag(LocalDate snapshotDate, String cafeMemberTag);

  List<NewStartThanksRoster> findBySnapshotDateOrderByRosterIdAsc(LocalDate snapshotDate);
}
