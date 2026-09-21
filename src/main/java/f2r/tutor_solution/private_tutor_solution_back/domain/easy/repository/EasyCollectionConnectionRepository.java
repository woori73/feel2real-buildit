package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnectionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EasyCollectionConnectionRepository
    extends JpaRepository<EasyCollectionConnection, Long> {

  Optional<EasyCollectionConnection> findByBusiness_BusinessIdAndSource_SourceIdAndLabel(
      Long businessId, Long sourceId, String label);

  List<EasyCollectionConnection> findAllByBusiness_BusinessIdOrderByConnectionIdAsc(Long businessId);

  List<EasyCollectionConnection> findBySource_SourceIdAndStatus(Long sourceId, EasyCollectionConnectionStatus status);

  List<EasyCollectionConnection> findByBusiness_BusinessId(Long businessId);

  /**
   * 연결을 비관적 락(PESSIMISTIC_WRITE)으로 조회한다.
   * 동일 connection에 대한 동시 collection 실행을 방지하기 위해 사용.
   * 이 메서드를 호출한 트랜잭션이 커밋/롤백될 때까지 다른 트랜잭션은 해당 행을 수정할 수 없다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM EasyCollectionConnection c WHERE c.connectionId = :connectionId")
  Optional<EasyCollectionConnection> findByIdWithLock(@Param("connectionId") Long connectionId);
}
