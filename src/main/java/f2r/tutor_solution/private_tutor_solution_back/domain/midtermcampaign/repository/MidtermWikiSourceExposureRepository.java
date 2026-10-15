package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermWikiSourceExposure;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MidtermWikiSourceExposureRepository
    extends JpaRepository<MidtermWikiSourceExposure, Long> {

  List<MidtermWikiSourceExposure> findByCampaignId(Long campaignId);

  List<MidtermWikiSourceExposure>
      findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(Long campaignId);

  Optional<MidtermWikiSourceExposure>
      findByCampaignIdAndSourceKindAndSourceId(Long campaignId, String sourceKind, long sourceId);

  @Modifying(flushAutomatically = true)
  @Query("""
      update MidtermWikiSourceExposure e
         set e.featured = false
       where e.campaignId = :campaignId
         and e.featured = true
         and (:exceptId is null or e.exposureId <> :exceptId)
      """)
  int clearFeatured(Long campaignId, Long exceptId);
}
