package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetAssignment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetAssignmentRepository extends JpaRepository<WorksheetAssignment, Long> {
  List<WorksheetAssignment> findAllByWorksheetIdOrderByAssignmentIdAsc(Long worksheetId);

  List<WorksheetAssignment> findAllByWorksheetIdInAndRemovedAtIsNull(Collection<Long> worksheetIds);

  Optional<WorksheetAssignment> findByWorksheetIdAndStudentId(Long worksheetId, Long studentId);

  void deleteAllByWorksheetId(Long worksheetId);
}
