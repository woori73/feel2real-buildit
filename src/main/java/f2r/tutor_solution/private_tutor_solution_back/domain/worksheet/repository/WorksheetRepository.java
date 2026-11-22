package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.Worksheet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetRepository extends JpaRepository<Worksheet, Long> {
  List<Worksheet> findAllByBusinessIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long businessId);

  List<Worksheet> findAllByBusinessIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Long businessId);
}
