package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeSurgeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WemadeSurgeAnalysisRepository extends JpaRepository<WemadeSurgeAnalysis, Long> {
    List<WemadeSurgeAnalysis> findBySymbolOrderBySurgeDateDesc(String symbol);
    boolean existsByContentHash(String hash);
    List<WemadeSurgeAnalysis> findBySymbolAndSurgeDateBetweenOrderBySurgeDateAsc(
            String symbol, LocalDate from, LocalDate to);
}
