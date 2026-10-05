package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeAnomalyMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WemadeAnomalyMetricRepository extends JpaRepository<WemadeAnomalyMetric, Long> {
    List<WemadeAnomalyMetric> findBySymbolAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            String symbol, LocalDateTime from, LocalDateTime to);
    List<WemadeAnomalyMetric> findBySymbolAndMetricTypeOrderByMeasuredAtDesc(
            String symbol, String metricType);
}
