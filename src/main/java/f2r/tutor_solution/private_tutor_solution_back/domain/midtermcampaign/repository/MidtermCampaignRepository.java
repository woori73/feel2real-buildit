package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermCampaignRepository extends JpaRepository<MidtermCampaign, Long> {

  Optional<MidtermCampaign> findByAcademy_AcademyId(Long academyId);

  boolean existsByAcademy_AcademyId(Long academyId);
}
