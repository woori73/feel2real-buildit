package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentWeekdaySchedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyStudentWeekdayScheduleRepository extends JpaRepository<EasyStudentWeekdaySchedule, Long> {
  List<EasyStudentWeekdaySchedule> findAllByBusiness_BusinessId(Long businessId);
  Optional<EasyStudentWeekdaySchedule> findByBusiness_BusinessIdAndStudent_StudentId(Long businessId, Long studentId);
}
