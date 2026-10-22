package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AssistantDraftRepository extends JpaRepository<AssistantDraft,String> {
  List<AssistantDraft> findTop30ByOwnerIdOrderByCreatedAtDesc(Long ownerId);
  List<AssistantDraft> findByOwnerIdAndStatusAndExpiresAtBefore(Long ownerId,String status,java.time.LocalDateTime before);
  long countByOwnerIdAndStatus(Long ownerId,String status);
  @Modifying(flushAutomatically=true)
  @Query("update AssistantDraft d set d.status=:status where d.id=:id and d.ownerId=:owner and d.status='PENDING' and d.expiresAt>:now")
  int claimPending(@Param("id") String id,@Param("owner") Long owner,@Param("status") String status,@Param("now") java.time.LocalDateTime now);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from AssistantDraft d where d.id=:id and d.ownerId=:owner")
  Optional<AssistantDraft> ownedForUpdate(@Param("id") String id,@Param("owner") Long owner);
}
