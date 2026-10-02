package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse.PeriodMetric;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse.RecentDownload;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse.RecentVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse.TopDownload;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse.ServiceTraffic;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsServiceArea;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.SitePageVisitRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 "통합 Analytics" — F2R 전체/Wiki/NewStart 방문(UV/PV) + Wiki 다운로드 통계를 한 번에 조립한다.
 * {@code WikiOpsOverviewService}와 동일하게 별도 통계 DB 없이 이벤트 로그 테이블을 직접 집계한다.
 * 서비스별 수치는 {@code AnalyticsServiceArea} 분류(과거 row 포함, V1019 재분류)를 그대로 따른다.
 */
@Service
@Transactional(readOnly = true)
public class AdminAnalyticsService {

  private static final int RECENT_VISIT_COUNT = 20;
  private static final int RECENT_DOWNLOAD_COUNT = 20;
  private static final int TOP_DOWNLOAD_COUNT = 10;

  private final SitePageVisitRepository visitRepository;
  private final WikiDownloadLogRepository downloadRepository;
  private final Clock clock;

  @Autowired
  public AdminAnalyticsService(
      SitePageVisitRepository visitRepository, WikiDownloadLogRepository downloadRepository) {
    this(visitRepository, downloadRepository, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  AdminAnalyticsService(
      SitePageVisitRepository visitRepository, WikiDownloadLogRepository downloadRepository, Clock clock) {
    this.visitRepository = visitRepository;
    this.downloadRepository = downloadRepository;
    this.clock = clock;
  }

  public AdminAnalyticsResponse summary() {
    LocalDate today = LocalDate.now(clock);
    LocalDate from7 = today.minusDays(6);
    LocalDate from30 = today.minusDays(29);

    PeriodMetric siteVisitors = new PeriodMetric(
        visitRepository.countDistinctVisitors(today, today),
        visitRepository.countDistinctVisitors(from7, today),
        visitRepository.countDistinctVisitors(from30, today),
        visitRepository.countDistinctVisitorsAllTime());

    PeriodMetric siteViews = new PeriodMetric(
        visitRepository.countPageViews(today, today),
        visitRepository.countPageViews(from7, today),
        visitRepository.countPageViews(from30, today),
        visitRepository.countPageViewsAllTime());

    PeriodMetric wikiVisitors = new PeriodMetric(
        visitRepository.countDistinctWikiVisitors(today, today),
        visitRepository.countDistinctWikiVisitors(from7, today),
        visitRepository.countDistinctWikiVisitors(from30, today),
        visitRepository.countDistinctWikiVisitorsAllTime());

    PeriodMetric wikiViews = new PeriodMetric(
        visitRepository.countWikiPageViews(today, today),
        visitRepository.countWikiPageViews(from7, today),
        visitRepository.countWikiPageViews(from30, today),
        visitRepository.countWikiPageViewsAllTime());

    PeriodMetric newStartVisitors = new PeriodMetric(
        visitRepository.countDistinctVisitorsByArea(AnalyticsServiceArea.NEWSTART, today, today),
        visitRepository.countDistinctVisitorsByArea(AnalyticsServiceArea.NEWSTART, from7, today),
        visitRepository.countDistinctVisitorsByArea(AnalyticsServiceArea.NEWSTART, from30, today),
        visitRepository.countDistinctVisitorsByAreaAllTime(AnalyticsServiceArea.NEWSTART));

    PeriodMetric wikiDownloads = new PeriodMetric(
        downloadRepository.countDownloads(today, today),
        downloadRepository.countDownloads(from7, today),
        downloadRepository.countDownloads(from30, today),
        downloadRepository.countDownloadsAllTime());

    PeriodMetric botRequests = new PeriodMetric(
        visitRepository.countBotRequests(today, today),
        visitRepository.countBotRequests(from7, today),
        visitRepository.countBotRequests(from30, today),
        visitRepository.countBotRequestsAllTime());

    var serviceTraffic = Arrays.stream(AnalyticsServiceArea.values())
        .map(area -> new ServiceTraffic(area.name(),
            new PeriodMetric(
                visitRepository.countDistinctVisitorsByArea(area, today, today),
                visitRepository.countDistinctVisitorsByArea(area, from7, today),
                visitRepository.countDistinctVisitorsByArea(area, from30, today),
                visitRepository.countDistinctVisitorsByAreaAllTime(area)),
            new PeriodMetric(
                visitRepository.countPageViewsByArea(area, today, today),
                visitRepository.countPageViewsByArea(area, from7, today),
                visitRepository.countPageViewsByArea(area, from30, today),
                visitRepository.countPageViewsByAreaAllTime(area))))
        .toList();

    LocalDate statsSince = visitRepository.findEarliestVisitDate().orElse(null);

    var recentVisits = visitRepository.findRecentWiki(PageRequest.of(0, RECENT_VISIT_COUNT)).stream()
        .map(v -> new RecentVisit(
            v.getDisplayName(), v.getRole(), v.isWiki() ? "WIKI" : "F2R", v.getPath(), v.getVisitedAt()))
        .toList();

    var recentAllVisits = visitRepository.findRecentHuman(PageRequest.of(0, RECENT_VISIT_COUNT)).stream()
        .map(v -> new RecentVisit(
            v.getDisplayName(), v.getRole(), v.getServiceArea().name(), v.getPath(), v.getVisitedAt()))
        .toList();

    var recentDownloads = downloadRepository.findRecent(PageRequest.of(0, RECENT_DOWNLOAD_COUNT)).stream()
        .map(d -> new RecentDownload(
            d.getDisplayName(), d.getRole(), d.getMaterialCategory().name(), d.getMaterialTitle(),
            d.getDownloadedAt()))
        .toList();

    var topDownloads = downloadRepository.findTopDownloads(PageRequest.of(0, TOP_DOWNLOAD_COUNT)).stream()
        .map(row -> new TopDownload(row.getCategory(), row.getTitle(), row.getDownloadCount()))
        .toList();

    return new AdminAnalyticsResponse(
        siteVisitors, siteViews, wikiVisitors, wikiViews, newStartVisitors, wikiDownloads, botRequests, statsSince,
        serviceTraffic, recentAllVisits, recentVisits, recentDownloads, topDownloads);
  }
}
