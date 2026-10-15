package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermMaterialRepository extends JpaRepository<MidtermMaterial, Long> {

  List<MidtermMaterial> findByCampaignIdOrderBySortOrderAscMidtermMaterialIdAsc(Long campaignId);

  List<MidtermMaterial>
      findByCampaignIdAndPublishedTrueOrderBySortOrderAscMidtermMaterialIdAsc(Long campaignId);

  Optional<MidtermMaterial> findByMidtermMaterialIdAndCampaignId(
      Long midtermMaterialId, Long campaignId);
}
