package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * BuildIt 시각 편집기 초안 Repository.
 */
public interface VisualEditorDraftRepository
    extends JpaRepository<VisualEditorDraft, Long> {

  Optional<VisualEditorDraft> findByAcademyId(Long academyId);

  boolean existsByAcademyId(Long academyId);
}
