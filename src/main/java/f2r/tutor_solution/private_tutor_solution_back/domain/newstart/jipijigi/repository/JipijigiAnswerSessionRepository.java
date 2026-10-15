package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession.ReviewState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface JipijigiAnswerSessionRepository
        extends JpaRepository<JipijigiAnswerSession, Long>, JpaSpecificationExecutor<JipijigiAnswerSession> {

    Optional<JipijigiAnswerSession> findByFingerprint(String fingerprint);

    Optional<JipijigiAnswerSession> findByDraftId(Long draftId);

    List<JipijigiAnswerSession> findByQuestionIdOrderByAnsweredAtDesc(Long questionId);

    Page<JipijigiAnswerSession> findAllByOrderByAnsweredAtDesc(Pageable pageable);

    List<JipijigiAnswerSession> findByReviewStateOrderByAnsweredAtDesc(ReviewState reviewState);
}
