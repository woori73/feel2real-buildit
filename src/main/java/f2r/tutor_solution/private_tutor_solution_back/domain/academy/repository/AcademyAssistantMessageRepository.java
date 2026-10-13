package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAssistantMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyAssistantMessageRepository extends JpaRepository<AcademyAssistantMessage, Long> {

  boolean existsByAcademyId(Long academyId);

  /** 학원의 대화를 오래된 순으로 돌려준다. */
  List<AcademyAssistantMessage> findByAcademyIdOrderByMessageIdAsc(Long academyId);
}
