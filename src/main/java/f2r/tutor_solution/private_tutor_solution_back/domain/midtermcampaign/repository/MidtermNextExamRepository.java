package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermNextExam;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermNextExamRepository extends JpaRepository<MidtermNextExam, Long> {

  Optional<MidtermNextExam> findByCampaignId(Long campaignId);
}
