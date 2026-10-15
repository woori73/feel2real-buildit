package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JipijigiCommandRepository extends JpaRepository<JipijigiCommand, Long> {
    Page<JipijigiCommand> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
