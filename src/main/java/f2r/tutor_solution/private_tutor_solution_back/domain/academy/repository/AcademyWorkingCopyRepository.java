package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyWorkingCopy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyWorkingCopyRepository extends JpaRepository<AcademyWorkingCopy, Long> {

  /** 학원당 작업본은 하나다. 반드시 서버가 확정한 academyId로만 조회한다. */
  Optional<AcademyWorkingCopy> findByAcademyId(Long academyId);
}
