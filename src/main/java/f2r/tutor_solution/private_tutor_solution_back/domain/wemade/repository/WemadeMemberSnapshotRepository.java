package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeMemberSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WemadeMemberSnapshotRepository extends JpaRepository<WemadeMemberSnapshot, Long> {
    List<WemadeMemberSnapshot> findBySymbolAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            String symbol, LocalDateTime from, LocalDateTime to);
    List<WemadeMemberSnapshot> findBySymbolAndBrokerNameAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            String symbol, String brokerName, LocalDateTime from, LocalDateTime to);
}
