package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiTargetBoard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JipijigiTargetBoardRepository extends JpaRepository<JipijigiTargetBoard, Long> {
    Optional<JipijigiTargetBoard> findByClubIdAndMenuId(Long clubId, Long menuId);
    List<JipijigiTargetBoard> findByEnabledTrue();
}
