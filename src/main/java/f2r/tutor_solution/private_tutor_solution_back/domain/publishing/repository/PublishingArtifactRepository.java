package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingArtifact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishingArtifactRepository extends JpaRepository<PublishingArtifact, Long> {

  List<PublishingArtifact> findByJobIdOrderByIdDesc(Long jobId);
}
