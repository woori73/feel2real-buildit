package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.AcademyEasyBusinessLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyEasyBusinessLinkRepository
    extends JpaRepository<AcademyEasyBusinessLink, Long> {

  List<AcademyEasyBusinessLink> findAllByAcademy_AcademyIdOrderByBusiness_SortOrderAsc(
      Long academyId);

  Optional<AcademyEasyBusinessLink> findByAcademy_AcademyIdAndBusiness_BusinessId(
      Long academyId, Long businessId);

  boolean existsByAcademy_AcademyIdAndBusiness_BusinessId(Long academyId, Long businessId);

  long deleteByAcademy_AcademyIdAndBusiness_BusinessId(Long academyId, Long businessId);
}
