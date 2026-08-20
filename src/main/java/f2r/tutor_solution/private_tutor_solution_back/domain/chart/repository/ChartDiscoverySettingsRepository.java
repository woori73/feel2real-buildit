package f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoverySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartDiscoverySettingsRepository extends JpaRepository<ChartDiscoverySettings, String> {
}
