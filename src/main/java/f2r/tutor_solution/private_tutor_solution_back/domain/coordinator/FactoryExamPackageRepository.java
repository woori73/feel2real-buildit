package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryExamPackageRepository extends JpaRepository<FactoryExamPackage, String> {
  boolean existsByExamCodeAndJobIdNot(String examCode, String jobId);

  /** 아직 학생 페이지에 올리지 않은 묶음(자동 게시 후보). */
  List<FactoryExamPackage> findAllByExamIdIsNull();
}
