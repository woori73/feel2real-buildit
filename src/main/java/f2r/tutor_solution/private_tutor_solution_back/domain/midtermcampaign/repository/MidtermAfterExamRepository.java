package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermAfterExam;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermAfterExamRepository extends JpaRepository<MidtermAfterExam, Long> {

  Optional<MidtermAfterExam> findBySchoolCollectionId(Long schoolCollectionId);

  List<MidtermAfterExam> findBySchoolCollectionIdIn(List<Long> schoolCollectionIds);
}
