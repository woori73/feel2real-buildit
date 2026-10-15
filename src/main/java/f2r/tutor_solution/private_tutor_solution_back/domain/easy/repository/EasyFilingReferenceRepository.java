package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyFilingReference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyFilingReferenceRepository extends JpaRepository<EasyFilingReference, Long> {
  Optional<EasyFilingReference> findByBusiness_BusinessIdAndFiscalYear(Long businessId, int year);

  List<EasyFilingReference> findByBusiness_BusinessIdOrderByFiscalYearDesc(Long businessId);

  List<EasyFilingReference> findByFiscalYearOrderByFiscalYearDesc(int year);
}
