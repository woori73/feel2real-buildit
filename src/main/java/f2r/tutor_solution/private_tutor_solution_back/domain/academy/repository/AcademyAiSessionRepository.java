package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** BuildIt AI 채팅 세션 Repository (P1). */
public interface AcademyAiSessionRepository extends JpaRepository<AcademyAiSession, Long> {

  List<AcademyAiSession> findByAcademyIdOrderByStartedAtDesc(Long academyId);
}
