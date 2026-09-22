package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCardEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCardEventRepository extends JpaRepository<EasyCardEvent, Long> {
  Optional<EasyCardEvent> findByBusiness_BusinessIdAndFingerprint(Long businessId, String fingerprint);
  List<EasyCardEvent> findByBusiness_BusinessIdAndReviewStatusOrderByOccurredAtAsc(
      Long businessId, EasyCardEvent.ReviewStatus reviewStatus);
}
