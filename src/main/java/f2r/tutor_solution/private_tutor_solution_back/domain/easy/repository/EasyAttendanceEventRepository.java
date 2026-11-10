package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAttendanceEvent;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyAttendanceEventRepository extends JpaRepository<EasyAttendanceEvent, Long> {
  List<EasyAttendanceEvent> findAllByStudent_StudentIdInAndOccurredOnBetweenOrderByOccurredAtAsc(
      Collection<Long> studentIds, LocalDate from, LocalDate to);

  List<EasyAttendanceEvent> findAllByStudent_StudentIdOrderByOccurredAtDesc(Long studentId);

  boolean existsBySourceKey(String sourceKey);

  List<EasyAttendanceEvent> findAllBySourceKeyIn(Collection<String> sourceKeys);
}
