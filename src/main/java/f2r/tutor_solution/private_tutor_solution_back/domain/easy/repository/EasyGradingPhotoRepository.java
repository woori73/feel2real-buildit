package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyGradingPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyGradingPhotoRepository extends JpaRepository<EasyGradingPhoto, Long> {
  List<EasyGradingPhoto> findByGrading_GradingIdOrderByPhotoIdAsc(Long gradingId);
}
