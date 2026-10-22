package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssistantAuditRepository extends JpaRepository<AssistantAudit,Long> {
  List<AssistantAudit> findTop50ByOwnerIdOrderByIdDesc(Long ownerId);
}
