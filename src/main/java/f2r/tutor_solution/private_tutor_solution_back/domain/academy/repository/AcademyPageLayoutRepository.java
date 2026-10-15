package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcademyPageLayoutRepository extends JpaRepository<AcademyPageLayout, Long> {

  Optional<AcademyPageLayout> findByAcademy_AcademyId(Long academyId);

  boolean existsByAcademy_AcademyId(Long academyId);
}
