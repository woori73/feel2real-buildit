package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTask;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCollectorTaskRepository extends JpaRepository<EasyCollectorTask, Long> {
  Optional<EasyCollectorTask> findFirstByCollector_CollectorIdAndStatusOrderByTaskIdAsc(
      Long collectorId, EasyCollectorTaskStatus status);

  List<EasyCollectorTask> findAllByBusiness_BusinessIdOrderByTaskIdDesc(Long businessId);

  boolean existsByCollector_CollectorIdAndSourceCodeAndDataTypeAndRequestedFromAndRequestedToAndStatusNotIn(
      Long collectorId, String sourceCode, String dataType, LocalDate requestedFrom,
      LocalDate requestedTo, List<EasyCollectorTaskStatus> terminalStatuses);
}
