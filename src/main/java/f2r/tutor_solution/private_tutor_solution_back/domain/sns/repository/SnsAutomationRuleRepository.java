package f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnsAutomationRuleRepository extends JpaRepository<SnsAutomationRule, Long> {

  List<SnsAutomationRule> findByEnabledTrue();

  long countByBrandId(Long brandId);

  long countByReferenceImageId(Long referenceImageId);

  long countByAccountId(Long accountId);

  /**
   * 오늘 실행 권리를 한 번만 가져간다(서버가 여럿이거나 같은 분에 두 번 돌아도 한 번만 실행). 1이면 이번 실행이 가져감.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update SnsAutomationRule r set r.lastRunOn = :today where r.id = :id and (r.lastRunOn is null or r.lastRunOn < :today)")
  int claimRun(@Param("id") Long id, @Param("today") LocalDate today);
}
