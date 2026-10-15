package f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidateStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartCandidateRepository extends JpaRepository<ChartCandidate, Long> {
  List<ChartCandidate> findByZoneId(String zoneId, Sort sort);
  Optional<ChartCandidate> findByZoneIdAndPersonKeyAndSourceUrlHash(String zoneId, String personKey, String sourceUrlHash);
  List<ChartCandidate> findByZoneIdAndStatusIn(String zoneId, Collection<ChartCandidateStatus> statuses, Sort sort);
}
