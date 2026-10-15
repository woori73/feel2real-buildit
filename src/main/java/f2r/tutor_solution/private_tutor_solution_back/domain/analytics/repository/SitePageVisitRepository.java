package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.SitePageVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsServiceArea;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SitePageVisitRepository extends JpaRepository<SitePageVisit, Long> {

  @Query("select count(distinct v.visitorKey) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN "
      + "and v.visitDate between :from and :to")
  long countDistinctVisitors(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(v) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN "
      + "and v.visitDate between :from and :to")
  long countPageViews(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(distinct v.visitorKey) from SitePageVisit v "
      + "where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN and v.wiki = true and v.visitDate between :from and :to")
  long countDistinctWikiVisitors(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(v) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN and v.wiki = true "
      + "and v.visitDate between :from and :to")
  long countWikiPageViews(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(distinct v.visitorKey) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN")
  long countDistinctVisitorsAllTime();

  @Query("select count(v) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN")
  long countPageViewsAllTime();

  @Query("select count(distinct v.visitorKey) from SitePageVisit v "
      + "where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN and v.wiki = true")
  long countDistinctWikiVisitorsAllTime();

  @Query("select count(v) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN and v.wiki = true")
  long countWikiPageViewsAllTime();

  @Query("select min(v.visitDate) from SitePageVisit v")
  Optional<LocalDate> findEarliestVisitDate();

  @Query("select count(distinct v.visitorKey) from SitePageVisit v "
      + "where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN and v.serviceArea = :area and v.visitDate between :from and :to")
  long countDistinctVisitorsByArea(@Param("area") AnalyticsServiceArea area,
      @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(v) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN "
      + "and v.serviceArea = :area and v.visitDate between :from and :to")
  long countPageViewsByArea(@Param("area") AnalyticsServiceArea area,
      @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(distinct v.visitorKey) from SitePageVisit v "
      + "where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN and v.serviceArea = :area")
  long countDistinctVisitorsByAreaAllTime(@Param("area") AnalyticsServiceArea area);

  @Query("select count(v) from SitePageVisit v "
      + "where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN and v.serviceArea = :area")
  long countPageViewsByAreaAllTime(@Param("area") AnalyticsServiceArea area);

  @Query("select count(v) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.BOT "
      + "and v.visitDate between :from and :to")
  long countBotRequests(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(v) from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.BOT")
  long countBotRequestsAllTime();

  /** 관리자 Analytics "최근 Wiki 방문" — Wiki 화면 방문만, 최신순. */
  @Query("select v from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN and v.wiki = true order by v.visitedAt desc")
  List<SitePageVisit> findRecentWiki(Pageable pageable);

  /** 내부 visitor key는 반환하지 않는 관리자용 최근 실제 방문 목록. */
  @Query("select v from SitePageVisit v where v.trafficKind = f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind.HUMAN order by v.visitedAt desc")
  List<SitePageVisit> findRecentHuman(Pageable pageable);
}
