package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JipijigiQuestionRepository extends JpaRepository<JipijigiQuestion, Long> {
    Optional<JipijigiQuestion> findByClubIdAndArticleId(Long clubId, String articleId);

    Page<JipijigiQuestion> findAllByOrderByWrittenAtDesc(Pageable pageable);
}
