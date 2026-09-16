package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyImportRowRepository extends JpaRepository<EasyImportRow, Long> {
  List<EasyImportRow> findByBatch_BatchIdOrderByRowNoAsc(Long batchId);

  List<EasyImportRow> findByBatch_BatchIdAndStatusOrderByRowNoAsc(
      Long batchId, EasyImportRowStatus status);
}
