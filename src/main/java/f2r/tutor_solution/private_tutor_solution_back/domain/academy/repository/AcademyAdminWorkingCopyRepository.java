package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAdminWorkingCopy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyAdminWorkingCopyRepository extends JpaRepository<AcademyAdminWorkingCopy, Long> {

  Optional<AcademyAdminWorkingCopy> findByAcademyId(Long academyId);
}
