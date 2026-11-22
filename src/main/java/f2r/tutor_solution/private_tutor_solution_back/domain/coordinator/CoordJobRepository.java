package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoordJobRepository extends JpaRepository<CoordJob, String> {
  Optional<CoordJob> findByUserIdAndRequestId(Long userId, String requestId);
  List<CoordJob> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

  List<CoordJob> findTop50ByUserIdAndKindOrderByCreatedAtAsc(Long userId, String kind);

  /** 제작 현황판: 최근에 맡긴 것부터 100건. */
  List<CoordJob> findTop100ByUserIdAndKindOrderByCreatedAtDesc(Long userId, String kind);
}
