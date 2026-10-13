package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyImportBatchRepository extends JpaRepository<EasyImportBatch, Long> {
  List<EasyImportBatch> findAllByBusiness_BusinessIdOrderByBatchIdDesc(Long businessId);

  Optional<EasyImportBatch> findByBatchIdAndBusiness_BusinessId(Long batchId, Long businessId);

  long countByBusiness_BusinessIdAndStatus(Long businessId, EasyImportStatus status);
}
