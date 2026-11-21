package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentAppCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAppCodeRepository extends JpaRepository<StudentAppCode, Long> {
  Optional<StudentAppCode> findByCodeHash(String codeHash);
  List<StudentAppCode> findByBusinessIdAndStudentIdAndStatus(Long businessId, Long studentId, String status);
  List<StudentAppCode> findByBusinessIdAndStatus(Long businessId, String status);
}
