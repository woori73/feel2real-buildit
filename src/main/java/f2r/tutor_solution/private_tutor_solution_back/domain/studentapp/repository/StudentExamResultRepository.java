package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentExamResultRepository extends JpaRepository<StudentExamResult, Long> {
  Optional<StudentExamResult> findByAttemptId(Long attemptId);
  long countByBusinessIdAndStudentIdAndExamCode(Long businessId, Long studentId, String examCode);
  List<StudentExamResult> findByBusinessIdAndStudentIdOrderBySubmittedAtDesc(Long businessId, Long studentId);
  List<StudentExamResult> findTop200ByBusinessIdOrderBySubmittedAtDesc(Long businessId);
  List<StudentExamResult> findByBusinessIdAndCheckedAtIsNullOrderBySubmittedAtDesc(Long businessId);
  long countByBusinessIdAndCheckedAtIsNull(Long businessId);
}
