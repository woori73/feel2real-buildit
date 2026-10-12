package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorPublished;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * BuildIt 시각 편집기 게시본 Repository.
 */
public interface VisualEditorPublishedRepository
    extends JpaRepository<VisualEditorPublished, Long> {

  Optional<VisualEditorPublished> findByAcademyId(Long academyId);
}
