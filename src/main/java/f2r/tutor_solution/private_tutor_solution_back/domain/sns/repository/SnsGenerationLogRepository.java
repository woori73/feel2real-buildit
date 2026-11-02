package f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsGenerationLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnsGenerationLogRepository extends JpaRepository<SnsGenerationLog, Long> {

  @Query("select coalesce(sum(l.costKrw), 0) from SnsGenerationLog l where l.createdAt >= :from")
  long sumCostSince(@Param("from") LocalDateTime from);

  @Query("select coalesce(sum(l.costKrw), 0) from SnsGenerationLog l where l.ruleId = :ruleId and l.createdAt >= :from")
  long sumCostSinceForRule(@Param("ruleId") Long ruleId, @Param("from") LocalDateTime from);

  @Query("select count(l) from SnsGenerationLog l where l.createdAt >= :from and l.kind <> f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsGenerationLog.Kind.PUBLISH")
  long countGenerationsSince(@Param("from") LocalDateTime from);

  List<SnsGenerationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
