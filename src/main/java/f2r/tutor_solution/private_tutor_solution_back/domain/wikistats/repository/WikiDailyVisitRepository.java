package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.entity.WikiDailyVisit;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiDailyVisitRepository extends JpaRepository<WikiDailyVisit, Long> {

  @Modifying
  @Query(value = """
      INSERT INTO wiki_daily_visits
        (visit_date, visit_count, created_at, updated_at)
      VALUES (:visitDate, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
      ON DUPLICATE KEY UPDATE
        visit_count = visit_count + 1,
        updated_at = CURRENT_TIMESTAMP(6)
      """, nativeQuery = true)
  void increment(@Param("visitDate") LocalDate visitDate);

  @Query("select coalesce(sum(v.visitCount), 0) from WikiDailyVisit v")
  long sumAll();

  @Query("""
      select v from WikiDailyVisit v
      where v.visitDate between :from and :to
      order by v.visitDate
      """)
  List<WikiDailyVisit> findRecent(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
