package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAnnualClosing;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyAnnualClosingRepository extends JpaRepository<EasyAnnualClosing, Long> {
  List<EasyAnnualClosing> findByBusiness_BusinessIdOrderByFiscalYearDesc(Long businessId);

  List<EasyAnnualClosing> findByFiscalYearOrderByFiscalYearDesc(int fiscalYear);

  Optional<EasyAnnualClosing> findByBusiness_BusinessIdAndFiscalYear(Long businessId, int fiscalYear);
}
