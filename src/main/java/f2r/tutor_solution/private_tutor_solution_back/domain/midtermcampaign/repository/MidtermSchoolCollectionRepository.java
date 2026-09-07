package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermSchoolCollection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermSchoolCollectionRepository
    extends JpaRepository<MidtermSchoolCollection, Long> {

  List<MidtermSchoolCollection> findByCampaignIdOrderBySortOrderAscSchoolCollectionIdAsc(
      Long campaignId);

  List<MidtermSchoolCollection>
      findByCampaignIdAndPublishedTrueOrderBySortOrderAscSchoolCollectionIdAsc(Long campaignId);

  Optional<MidtermSchoolCollection> findBySchoolCollectionIdAndCampaignId(
      Long schoolCollectionId, Long campaignId);
}
