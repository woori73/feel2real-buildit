package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishingVersionRepository extends JpaRepository<PublishingVersion, Long> {

  List<PublishingVersion> findByJobIdOrderByIdDesc(Long jobId);
}
