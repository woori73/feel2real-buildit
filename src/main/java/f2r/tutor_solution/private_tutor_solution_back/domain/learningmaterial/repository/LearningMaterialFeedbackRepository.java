package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningMaterialFeedbackRepository extends JpaRepository<LearningMaterialFeedback, Long> {

  List<LearningMaterialFeedback> findByMaterial_MaterialIdOrderByOrderNumAsc(Long materialId);

  void deleteByMaterial_MaterialId(Long materialId);
}
