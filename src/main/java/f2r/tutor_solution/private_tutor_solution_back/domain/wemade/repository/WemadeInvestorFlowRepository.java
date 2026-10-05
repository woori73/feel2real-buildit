package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeInvestorFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface WemadeInvestorFlowRepository extends JpaRepository<WemadeInvestorFlow, WemadeInvestorFlow.FlowId> {
    List<WemadeInvestorFlow> findByTradeDateOrderByInvestorType(LocalDate d);
        List<WemadeInvestorFlow> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate s, LocalDate e);
    boolean existsByContentHash(String hash);
}
