package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermPrepStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MidtermPrepStatusRepository extends JpaRepository<MidtermPrepStatus, Long> {

  List<MidtermPrepStatus> findBySchoolCollectionIdOrderBySortOrderAscPrepStatusIdAsc(
      Long schoolCollectionId);

  List<MidtermPrepStatus> findBySchoolCollectionIdInOrderBySortOrderAscPrepStatusIdAsc(
      List<Long> schoolCollectionIds);

  void deleteBySchoolCollectionId(Long schoolCollectionId);
}
