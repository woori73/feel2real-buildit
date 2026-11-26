package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyStudentRepository extends JpaRepository<EasyStudent, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from EasyStudent s where s.studentId in :ids order by s.studentId")
  List<EasyStudent> lockForAttendanceSplit(@Param("ids") List<Long> ids);

  List<EasyStudent> findAllByOrderByNameAsc();

  long countByStatus(EasyStudentStatus status);

  List<EasyStudent> findAllByStatus(EasyStudentStatus status);
}
