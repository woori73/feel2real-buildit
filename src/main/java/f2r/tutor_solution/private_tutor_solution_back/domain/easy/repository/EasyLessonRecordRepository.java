package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLessonRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyLessonRecordRepository extends JpaRepository<EasyLessonRecord, Long> {
  List<EasyLessonRecord> findByBusiness_BusinessIdAndStudent_StudentIdOrderByLessonDateDescLessonRecordIdDesc(
      Long businessId, Long studentId);
}
