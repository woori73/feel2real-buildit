package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface EasyAgentActionRepository extends JpaRepository<EasyAgentAction,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select a from EasyAgentAction a where a.actionId=:actionId and a.business.businessId=:businessId and a.ownerUserId=:ownerUserId")
 Optional<EasyAgentAction> findOwnedForUpdate(@Param("actionId") Long actionId,@Param("businessId") Long businessId,@Param("ownerUserId") Long ownerUserId);
 Optional<EasyAgentAction> findFirstBySession_SessionIdAndStatusOrderByActionIdDesc(Long sessionId,EasyAgentActionStatus status);
}
