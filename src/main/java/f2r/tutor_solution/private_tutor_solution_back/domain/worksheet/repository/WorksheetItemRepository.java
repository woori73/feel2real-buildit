package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetItemRepository extends JpaRepository<WorksheetItem, Long> {
  List<WorksheetItem> findAllByWorksheetIdOrderByOrderNoAsc(Long worksheetId);

  void deleteAllByWorksheetId(Long worksheetId);
}
