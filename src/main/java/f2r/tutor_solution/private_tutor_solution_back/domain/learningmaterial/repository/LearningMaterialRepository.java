package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, Long> {

  List<LearningMaterial> findByStudent_StudentIdOrderByAssignedAtDesc(Long studentId);

  List<LearningMaterial> findByConversation_ConversationIdOrderByAssignedAtDesc(Long conversationId);
}
