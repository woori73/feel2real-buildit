package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAgentMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EasyAgentMessageRepository extends JpaRepository<EasyAgentMessage,Long>{
 List<EasyAgentMessage> findTop20BySession_SessionIdOrderByMessageIdDesc(Long sessionId);
 List<EasyAgentMessage> findBySession_SessionIdOrderByMessageIdAsc(Long sessionId);
}
