package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetFavoriteRepository extends JpaRepository<WorksheetFavorite, Long> {
  List<WorksheetFavorite> findAllByUserId(Long userId);

  Optional<WorksheetFavorite> findByWorksheetIdAndUserId(Long worksheetId, Long userId);

  void deleteAllByWorksheetId(Long worksheetId);
}
