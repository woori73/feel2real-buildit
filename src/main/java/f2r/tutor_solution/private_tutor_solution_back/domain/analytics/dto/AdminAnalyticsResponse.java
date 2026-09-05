package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 "통합 Analytics" 화면 데이터. IP 등 개인정보는 담지 않는다 — 표시 이름·role·시각까지만.
 * {@code statsSince}는 통계 수집이 시작된 최초 방문 날짜(이전 데이터는 없음을 프론트가 안내).
 */
public record AdminAnalyticsResponse(
    PeriodMetric siteVisitors,
    PeriodMetric siteViews,
    PeriodMetric wikiVisitors,
    PeriodMetric wikiViews,
    PeriodMetric wikiDownloads,
    PeriodMetric botRequests,
    LocalDate statsSince,
    List<ServiceTraffic> serviceTraffic,
    List<RecentVisit> recentVisits,
    List<RecentVisit> recentWikiVisits,
    List<RecentDownload> recentDownloads,
    List<TopDownload> topDownloads) {

  public record PeriodMetric(long today, long recent7, long recent30, long total) {}

  public record ServiceTraffic(String service, PeriodMetric visitors, PeriodMetric views) {}

  public record RecentVisit(String displayName, String role, String area, String path, LocalDateTime visitedAt) {}

  public record RecentDownload(
      String displayName, String role, String materialCategory, String materialTitle, LocalDateTime downloadedAt) {}

  public record TopDownload(String materialCategory, String materialTitle, long downloadCount) {}
}
