package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyGradingRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyGradingRecordRepository extends JpaRepository<EasyGradingRecord, Long> {
  List<EasyGradingRecord> findByBusiness_BusinessIdAndStudent_StudentIdOrderByGradedOnDescGradingIdDesc(
      Long businessId, Long studentId);

  List<EasyGradingRecord> findByLessonRecord_LessonRecordId(Long lessonRecordId);
}
