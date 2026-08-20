package f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartEducator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartEducatorRepository extends JpaRepository<ChartEducator, Long> {
  Optional<ChartEducator> findByPersonKey(String personKey);
  boolean existsByPersonKey(String personKey);
  List<ChartEducator> findByZoneId(String zoneId, Sort sort);
}
