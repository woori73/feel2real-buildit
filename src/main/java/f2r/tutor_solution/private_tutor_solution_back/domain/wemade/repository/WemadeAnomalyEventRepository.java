package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeAnomalyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WemadeAnomalyEventRepository extends JpaRepository<WemadeAnomalyEvent, Long> {
    List<WemadeAnomalyEvent> findBySymbolOrderByDetectedAtDesc(String symbol);
    List<WemadeAnomalyEvent> findBySymbolAndAnalysisTypeOrderByDetectedAtDesc(
            String symbol, String analysisType);
    List<WemadeAnomalyEvent> findBySymbolAndExchangeOrderByDetectedAtDesc(
            String symbol, String exchange);
}
