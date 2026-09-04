package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.SitePageVisit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SitePageVisitRepository extends JpaRepository<SitePageVisit, Long> {

  @Query("select count(distinct v.visitorKey) from SitePageVisit v where v.visitDate between :from and :to")
  long countDistinctVisitors(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(v) from SitePageVisit v where v.visitDate between :from and :to")
  long countPageViews(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(distinct v.visitorKey) from SitePageVisit v "
      + "where v.wiki = true and v.visitDate between :from and :to")
  long countDistinctWikiVisitors(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(v) from SitePageVisit v where v.wiki = true and v.visitDate between :from and :to")
  long countWikiPageViews(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(distinct v.visitorKey) from SitePageVisit v")
  long countDistinctVisitorsAllTime();

  @Query("select count(v) from SitePageVisit v")
  long countPageViewsAllTime();

  @Query("select count(distinct v.visitorKey) from SitePageVisit v where v.wiki = true")
  long countDistinctWikiVisitorsAllTime();

  @Query("select count(v) from SitePageVisit v where v.wiki = true")
  long countWikiPageViewsAllTime();

  @Query("select min(v.visitDate) from SitePageVisit v")
  Optional<LocalDate> findEarliestVisitDate();

  /** 관리자 Analytics "최근 Wiki 방문" — Wiki 화면 방문만, 최신순. */
  @Query("select v from SitePageVisit v where v.wiki = true order by v.visitedAt desc")
  List<SitePageVisit> findRecentWiki(Pageable pageable);
}
