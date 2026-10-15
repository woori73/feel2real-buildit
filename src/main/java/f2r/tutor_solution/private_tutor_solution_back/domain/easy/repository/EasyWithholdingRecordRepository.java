package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyWithholdingRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyWithholdingRecordRepository extends JpaRepository<EasyWithholdingRecord, Long> {
  List<EasyWithholdingRecord> findByBusiness_BusinessIdOrderByPaidOnDescRecordIdDesc(Long businessId);

  List<EasyWithholdingRecord> findAllByOrderByPaidOnDescRecordIdDesc();
}
