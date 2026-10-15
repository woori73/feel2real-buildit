package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** BuildIt AI 작업 로그 Repository (P1). */
public interface AcademyAiActionRepository extends JpaRepository<AcademyAiAction, Long> {

  List<AcademyAiAction> findByAcademyIdOrderByCreatedAtDesc(Long academyId);

  List<AcademyAiAction> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
