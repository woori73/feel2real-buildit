package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermUpdateLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermUpdateLogRepository extends JpaRepository<MidtermUpdateLog, Long> {

  List<MidtermUpdateLog> findByCampaignIdOrderByLogDateDescSortOrderAscUpdateLogIdDesc(
      Long campaignId);

  List<MidtermUpdateLog>
      findByCampaignIdAndPublishedTrueOrderByLogDateDescSortOrderAscUpdateLogIdDesc(Long campaignId);

  Optional<MidtermUpdateLog> findByUpdateLogIdAndCampaignId(Long updateLogId, Long campaignId);
}
