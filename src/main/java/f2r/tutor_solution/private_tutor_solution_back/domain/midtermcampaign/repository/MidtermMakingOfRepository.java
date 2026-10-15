package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMakingOf;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermMakingOfRepository extends JpaRepository<MidtermMakingOf, Long> {

  List<MidtermMakingOf> findByCampaignIdOrderBySortOrderAscMakingOfIdAsc(Long campaignId);

  Optional<MidtermMakingOf> findByMakingOfIdAndCampaignId(Long makingOfId, Long campaignId);
}
