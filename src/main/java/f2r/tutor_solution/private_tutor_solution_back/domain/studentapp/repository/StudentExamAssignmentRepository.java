package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentExamAssignmentRepository extends JpaRepository<StudentExamAssignment, Long> {
  Optional<StudentExamAssignment> findByBusinessIdAndStudentIdAndExamCode(Long businessId, Long studentId, String examCode);
  List<StudentExamAssignment> findByBusinessIdAndStudentIdAndRemovedAtIsNullOrderByAssignedAtDesc(Long businessId, Long studentId);
}
