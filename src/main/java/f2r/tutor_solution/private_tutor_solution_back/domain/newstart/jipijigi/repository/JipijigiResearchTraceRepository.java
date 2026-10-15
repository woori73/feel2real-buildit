package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JipijigiResearchTraceRepository extends JpaRepository<JipijigiResearchTrace, Long> {
    List<JipijigiResearchTrace> findByDraftIdOrderByStepNoAscTraceIdAsc(Long draftId);

    List<JipijigiResearchTrace> findByQuestionIdOrderByStepNoAscTraceIdAsc(Long questionId);
}
