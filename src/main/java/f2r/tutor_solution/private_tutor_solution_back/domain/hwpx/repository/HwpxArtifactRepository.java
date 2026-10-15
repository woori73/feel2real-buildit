package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxArtifact;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HwpxArtifactRepository extends JpaRepository<HwpxArtifact, Long> {

  List<HwpxArtifact> findByJobId(Long jobId);

  Optional<HwpxArtifact> findFirstByJobIdOrderByIdDesc(Long jobId);
}
