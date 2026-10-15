package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun.RunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartOfficialDiscoveryRunRepository extends JpaRepository<NewStartOfficialDiscoveryRun, Long> {

  List<NewStartOfficialDiscoveryRun> findBySourceTypeOrderByRunStartedAtDesc(SourceType sourceType);

  Optional<NewStartOfficialDiscoveryRun> findFirstBySourceTypeAndStatusOrderByRunStartedAtDesc(
      SourceType sourceType, RunStatus status);

  List<NewStartOfficialDiscoveryRun> findByRunStartedAtBetweenOrderByRunStartedAtDesc(
      LocalDateTime start, LocalDateTime end);
}