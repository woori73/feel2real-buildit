package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademyRegionRepository extends JpaRepository<AcademyRegion, Long> {

  List<AcademyRegion> findByAcademy_AcademyIdOrderByRegionIdAsc(Long academyId);

  void deleteByAcademy_AcademyId(Long academyId);
}
