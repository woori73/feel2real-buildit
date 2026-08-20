package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyDailyVisit;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademyDailyVisitRepository extends JpaRepository<AcademyDailyVisit, Long> {

  @Modifying
  @Query(value = """
      INSERT INTO academy_daily_visits
        (academy_id, visit_date, visit_count, created_at, updated_at)
      VALUES (:academyId, :visitDate, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
      ON DUPLICATE KEY UPDATE
        visit_count = visit_count + 1,
        updated_at = CURRENT_TIMESTAMP(6)
      """, nativeQuery = true)
  void increment(@Param("academyId") Long academyId, @Param("visitDate") LocalDate visitDate);

  @Query("""
      select coalesce(sum(v.visitCount), 0)
      from AcademyDailyVisit v
      where v.academy.academyId = :academyId
      """)
  long sumByAcademyId(@Param("academyId") Long academyId);

  @Query("""
      select v
      from AcademyDailyVisit v
      where v.academy.academyId = :academyId
        and v.visitDate between :from and :to
      order by v.visitDate
      """)
  List<AcademyDailyVisit> findRecent(
      @Param("academyId") Long academyId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}

