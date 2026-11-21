package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamViewer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentExamViewerRepository extends JpaRepository<StudentExamViewer, Long> {
  Optional<StudentExamViewer> findByBusinessIdAndUserId(Long businessId, Long userId);
  List<StudentExamViewer> findByBusinessIdAndRevokedAtIsNullOrderByGrantedAtAsc(Long businessId);
  List<StudentExamViewer> findByUserIdAndRevokedAtIsNull(Long userId);
}
