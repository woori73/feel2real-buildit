package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryExamPackageRepository extends JpaRepository<FactoryExamPackage, String> {
  boolean existsByExamCodeAndJobIdNot(String examCode, String jobId);
}
