package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAgentSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EasyAgentSessionRepository extends JpaRepository<EasyAgentSession,Long>{
 Optional<EasyAgentSession> findBySessionIdAndBusiness_BusinessIdAndOwnerUserId(Long sessionId,Long businessId,Long ownerUserId);
}
