package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface EasyCollectionRunRepository extends JpaRepository<EasyCollectionRun, Long> {

  List<EasyCollectionRun> findAllByBusiness_BusinessIdOrderByRunIdDesc(Long businessId);

  List<EasyCollectionRun> findAllByOrderByRunIdDesc();

  boolean existsByConnection_ConnectionIdAndStatus(Long connectionId, EasyCollectionRunStatus status);

  List<EasyCollectionRun> findAllByConnection_ConnectionIdAndStatusOrderByRunIdAsc(
      Long connectionId, EasyCollectionRunStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EasyCollectionRun> findByIdWithLock(Long runId);

  Optional<EasyCollectionRun> findFirstByConnection_ConnectionIdOrderByRunIdDesc(Long connectionId);
}
