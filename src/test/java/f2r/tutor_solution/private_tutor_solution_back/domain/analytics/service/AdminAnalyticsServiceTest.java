package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.SitePageVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsServiceArea;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiDownloadLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.SitePageVisitRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository.TopDownloadRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

@DisplayName("AdminAnalyticsService — 통합 Analytics 집계")
class AdminAnalyticsServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);

  private SitePageVisitRepository visitRepository;
  private WikiDownloadLogRepository downloadRepository;
  private AdminAnalyticsService service;

  @BeforeEach
  void setUp() {
    visitRepository = mock(SitePageVisitRepository.class);
    downloadRepository = mock(WikiDownloadLogRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-09-05T04:00:00Z"), SEOUL);  // 13:00 KST
    service = new AdminAnalyticsService(visitRepository, downloadRepository, clock);

    when(visitRepository.countDistinctVisitors(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.countPageViews(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.countDistinctWikiVisitors(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.countWikiPageViews(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(0L);
    when(downloadRepository.countDownloads(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.countDistinctVisitorsAllTime()).thenReturn(0L);
    when(visitRepository.countPageViewsAllTime()).thenReturn(0L);
    when(visitRepository.countDistinctWikiVisitorsAllTime()).thenReturn(0L);
    when(visitRepository.countWikiPageViewsAllTime()).thenReturn(0L);
    when(downloadRepository.countDownloadsAllTime()).thenReturn(0L);
    when(visitRepository.countBotRequests(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.countBotRequestsAllTime()).thenReturn(0L);
    when(visitRepository.countDistinctVisitorsByArea(
        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.countPageViewsByArea(
        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.countDistinctVisitorsByAreaAllTime(ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.countPageViewsByAreaAllTime(ArgumentMatchers.any())).thenReturn(0L);
    when(visitRepository.findEarliestVisitDate()).thenReturn(Optional.empty());
    when(visitRepository.findRecentWiki(ArgumentMatchers.any())).thenReturn(List.of());
    when(visitRepository.findRecentHuman(ArgumentMatchers.any())).thenReturn(List.of());
    when(downloadRepository.findRecent(ArgumentMatchers.any())).thenReturn(List.of());
    when(downloadRepository.findTopDownloads(ArgumentMatchers.any())).thenReturn(List.of());
  }

  @Test
  @DisplayName("오늘/7일/30일/누적 기간을 오늘(clock) 기준으로 정확히 계산해 repository에 전달한다")
  void summary_queriesCorrectDateRanges() {
    service.summary();

    org.mockito.Mockito.verify(visitRepository).countDistinctVisitors(TODAY, TODAY);
    org.mockito.Mockito.verify(visitRepository).countDistinctVisitors(TODAY.minusDays(6), TODAY);
    org.mockito.Mockito.verify(visitRepository).countDistinctVisitors(TODAY.minusDays(29), TODAY);
  }

  @Test
  @DisplayName("statsSince: 최초 방문일이 없으면 null, 있으면 그대로 반환")
  void summary_statsSince() {
    AdminAnalyticsResponse none = service.summary();
    assertThat(none.statsSince()).isNull();

    when(visitRepository.findEarliestVisitDate()).thenReturn(Optional.of(LocalDate.of(2026, 8, 1)));
    AdminAnalyticsResponse withDate = service.summary();
    assertThat(withDate.statsSince()).isEqualTo(LocalDate.of(2026, 8, 1));
  }

  @Test
  @DisplayName("HOME~OTHER 6개 서비스의 UV/PV 기간별 집계를 응답한다")
  void summary_serviceTraffic() {
    when(visitRepository.countDistinctVisitorsByArea(
        AnalyticsServiceArea.CHART, TODAY, TODAY)).thenReturn(12L);
    when(visitRepository.countPageViewsByArea(
        AnalyticsServiceArea.CHART, TODAY, TODAY)).thenReturn(34L);

    var chart = service.summary().serviceTraffic().stream()
        .filter(row -> row.service().equals("CHART"))
        .findFirst().orElseThrow();

    assertThat(service.summary().serviceTraffic()).hasSize(6);
    assertThat(chart.visitors().today()).isEqualTo(12L);
    assertThat(chart.views().today()).isEqualTo(34L);
  }

  @Test
  @DisplayName("최근 Wiki 방문은 displayName/role/area/path/visitedAt으로 매핑된다")
  void summary_recentWikiVisitsMapping() {
    SitePageVisit visit = SitePageVisit.of(
        TODAY, LocalDateTime.of(2026, 9, 5, 14, 32), true, "/wiki/analysis",
        7L, "TUTOR", "박정민", "U:7");
    when(visitRepository.findRecentWiki(ArgumentMatchers.any())).thenReturn(List.of(visit));
    when(visitRepository.findRecentHuman(ArgumentMatchers.any())).thenReturn(List.of(visit));

    var recent = service.summary().recentWikiVisits();

    assertThat(recent).hasSize(1);
    assertThat(recent.get(0).displayName()).isEqualTo("박정민");
    assertThat(recent.get(0).role()).isEqualTo("TUTOR");
    assertThat(recent.get(0).area()).isEqualTo("WIKI");
    assertThat(recent.get(0).path()).isEqualTo("/wiki/analysis");
    assertThat(service.summary().recentVisits().get(0).area()).isEqualTo("WIKI");
  }

  @Test
  @DisplayName("최근 다운로드/TOP 다운로드가 응답에 매핑된다")
  void summary_downloadsMapping() {
    WikiDownloadLog log = WikiDownloadLog.of(
        TODAY, LocalDateTime.of(2026, 9, 5, 10, 0), WikiMaterialCategory.LEARNING, 9L, "고2 어휘",
        11L, "STUDENT", "김민수");
    when(downloadRepository.findRecent(ArgumentMatchers.any())).thenReturn(List.of(log));
    TopDownloadRow row = new TopDownloadRow() {
      public String getCategory() { return "LEARNING"; }
      public String getTitle() { return "고2 어휘"; }
      public long getDownloadCount() { return 42L; }
    };
    when(downloadRepository.findTopDownloads(ArgumentMatchers.any())).thenReturn(List.of(row));

    AdminAnalyticsResponse response = service.summary();

    assertThat(response.recentDownloads()).hasSize(1);
    assertThat(response.recentDownloads().get(0).displayName()).isEqualTo("김민수");
    assertThat(response.recentDownloads().get(0).materialCategory()).isEqualTo("LEARNING");
    assertThat(response.topDownloads()).hasSize(1);
    assertThat(response.topDownloads().get(0).downloadCount()).isEqualTo(42L);
  }
}
