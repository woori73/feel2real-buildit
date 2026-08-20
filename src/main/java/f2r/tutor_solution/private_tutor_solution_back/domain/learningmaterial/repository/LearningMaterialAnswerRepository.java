package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningMaterialAnswerRepository extends JpaRepository<LearningMaterialAnswer, Long> {

  List<LearningMaterialAnswer> findByMaterial_MaterialIdOrderByOrderNumAsc(Long materialId);

  void deleteByMaterial_MaterialId(Long materialId);
}
