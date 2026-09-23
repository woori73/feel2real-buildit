package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface JipijigiDraftRepository extends JpaRepository<JipijigiDraft, Long>, JpaSpecificationExecutor<JipijigiDraft> {
    List<JipijigiDraft> findByStatusOrderByUpdatedAtDesc(JipijigiDraft.DraftStatus status);

    List<JipijigiDraft> findAllByOrderByUpdatedAtDesc();
}
