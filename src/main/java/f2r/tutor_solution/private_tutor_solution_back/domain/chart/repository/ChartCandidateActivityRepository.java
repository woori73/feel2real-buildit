package f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidateActivity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartCandidateActivityRepository extends JpaRepository<ChartCandidateActivity, Long> {
  List<ChartCandidateActivity> findByCandidate_CandidateIdOrderByActivityIdAsc(Long candidateId);
  List<ChartCandidateActivity> findByCandidateInOrderByActivityIdAsc(Collection<ChartCandidate> candidates);
  boolean existsByCandidateAndSourceUrl(ChartCandidate candidate, String sourceUrl);
}
