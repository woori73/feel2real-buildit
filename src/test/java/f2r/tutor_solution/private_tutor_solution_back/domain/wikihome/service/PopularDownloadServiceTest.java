package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository.PopularDownloadRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.PopularDownloadItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.PopularDownloads;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.repository.WikiLearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PopularDownloadService — 24h 집계 · 7일 fallback · 순위 변동 · 학년 벌크 조회 · 캐시")
class PopularDownloadServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  // 2026-09-06T13:00 KST
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 13, 0);
  private static final LocalDateTime DAY_FROM = NOW.minusHours(24);   // 09-05 13:00
  private static final LocalDateTime PREV_FROM = DAY_FROM.minusHours(24); // 09-04 13:00
  private static final LocalDateTime WEEK_FROM = NOW.minusDays(7);    // 08-30 13:00

  private WikiDownloadLogRepository downloadRepository;
  private WikiExamOriginalRepository originalRepository;
  private WikiAnalysisMaterialRepository analysisRepository;
  private WikiLearningMaterialRepository learningRepository;
  private Clock clock;

  @BeforeEach
  void setUp() {
    downloadRepository = mock(WikiDownloadLogRepository.class);
    originalRepository = mock(WikiExamOriginalRepository.class);
    analysisRepository = mock(WikiAnalysisMaterialRepository.class);
    learningRepository = mock(WikiLearningMaterialRepository.class);
    clock = Clock.fixed(Instant.parse("2026-09-06T04:00:00Z"), SEOUL);
    when(originalRepository.findAllById(anyList())).thenReturn(List.of());
    when(analysisRepository.findAllById(anyList())).thenReturn(List.of());
    when(learningRepository.findAllById(anyList())).thenReturn(List.of());
  }

  private PopularDownloadService service(Duration ttl) {
    return new PopularDownloadService(
        downloadRepository, originalRepository, analysisRepository, learningRepository, clock, ttl);
  }

  @Test
  @DisplayName("24h 데이터가 충분하면 최근 24시간 기준, 순위는 repo 순서대로 1..N")
  void compute_uses24hWindow() {
    when(downloadRepository.countBetween(eq(DAY_FROM), eq(NOW))).thenReturn(30L);
    when(downloadRepository.aggregateBetween(eq(DAY_FROM), eq(NOW), any()))
        .thenReturn(List.of(row("ANALYSIS", 10L, "고2 분석", 38), row("ORIGINAL", 7L, "고1 문제지", 31)));
    when(downloadRepository.aggregateBetween(eq(PREV_FROM), eq(DAY_FROM), any())).thenReturn(List.of());

    PopularDownloads result = service(Duration.ZERO).compute();

    assertThat(result.periodLabel()).isEqualTo("최근 24시간");
    assertThat(result.periodHours()).isEqualTo(24);
    assertThat(result.items()).extracting(PopularDownloadItem::rank).containsExactly(1, 2);
    assertThat(result.items().get(0).downloadCount()).isEqualTo(38);
    assertThat(result.items().get(0).categoryLabel()).isEqualTo("분석 자료");
    assertThat(result.items().get(0).href()).isEqualTo("/wiki/analysis");
    assertThat(result.items().get(1).href()).isEqualTo("/wiki/originals");
  }

  @Test
  @DisplayName("24h 데이터가 너무 적으면 최근 7일로 fallback — 라벨과 집계 기간이 함께 바뀐다")
  void compute_fallsBackTo7Days() {
    when(downloadRepository.countBetween(eq(DAY_FROM), eq(NOW))).thenReturn(3L);
    when(downloadRepository.aggregateBetween(eq(WEEK_FROM), eq(NOW), any()))
        .thenReturn(List.of(row("LEARNING", 5L, "WEEKLY 01", 8)));
    when(downloadRepository.aggregateBetween(eq(WEEK_FROM.minusDays(7)), eq(WEEK_FROM), any()))
        .thenReturn(List.of());

    PopularDownloads result = service(Duration.ZERO).compute();

    assertThat(result.periodLabel()).isEqualTo("최근 7일");
    assertThat(result.periodHours()).isEqualTo(168);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).href()).isEqualTo("/wiki/learning/5");
  }

  @Test
  @DisplayName("순위 변동: 직전 창 대비 상승/신규를 계산한다")
  void compute_computesTrend() {
    when(downloadRepository.countBetween(any(), any())).thenReturn(50L);
    when(downloadRepository.aggregateBetween(eq(DAY_FROM), eq(NOW), any()))
        .thenReturn(List.of(row("ANALYSIS", 10L, "A", 20), row("ORIGINAL", 7L, "B", 15)));
    // 직전 창에서는 ORIGINAL:7 이 1위, ANALYSIS:10 은 없었음
    when(downloadRepository.aggregateBetween(eq(PREV_FROM), eq(DAY_FROM), any()))
        .thenReturn(List.of(row("ORIGINAL", 7L, "B", 40)));

    List<PopularDownloadItem> items = service(Duration.ZERO).compute().items();

    assertThat(items.get(0).trend()).isEqualTo("NEW");
    assertThat(items.get(1).trend()).isEqualTo("DOWN");
    assertThat(items.get(1).trendAmount()).isEqualTo(1);
  }

  @Test
  @DisplayName("학년은 원본 자료 테이블에서 벌크로 조회, 없는 자료는 null")
  void compute_resolvesGradeInBulk() {
    when(downloadRepository.countBetween(any(), any())).thenReturn(50L);
    when(downloadRepository.aggregateBetween(eq(DAY_FROM), eq(NOW), any()))
        .thenReturn(List.of(row("ORIGINAL", 7L, "있는 자료", 5), row("ORIGINAL", 99L, "삭제된 자료", 4)));
    when(downloadRepository.aggregateBetween(eq(PREV_FROM), eq(DAY_FROM), any())).thenReturn(List.of());
    WikiExamOriginal existing = mock(WikiExamOriginal.class);
    when(existing.getWikiExamOriginalId()).thenReturn(7L);
    when(existing.getGrade()).thenReturn(2);
    when(originalRepository.findAllById(anyList())).thenReturn(List.of(existing));

    List<PopularDownloadItem> items = service(Duration.ZERO).compute().items();

    assertThat(items.get(0).grade()).isEqualTo(2);
    assertThat(items.get(1).grade()).isNull();
    verify(originalRepository, times(1)).findAllById(anyList());
  }

  @Test
  @DisplayName("결과는 TTL 동안 캐시된다 — 반복 조회가 DB 를 다시 때리지 않는다")
  void getPopularDownloads_cachesWithinTtl() {
    when(downloadRepository.countBetween(any(), any())).thenReturn(50L);
    when(downloadRepository.aggregateBetween(any(), any(), any())).thenReturn(List.of());

    PopularDownloadService s = service(Duration.ofSeconds(60));
    s.getPopularDownloads();
    s.getPopularDownloads();
    s.getPopularDownloads();

    verify(downloadRepository, times(1)).countBetween(any(), any());
  }

  private static PopularDownloadRow row(String category, Long id, String title, long count) {
    return new PopularDownloadRow() {
      @Override public String getCategory() { return category; }
      @Override public Long getMaterialId() { return id; }
      @Override public String getTitle() { return title; }
      @Override public long getDownloadCount() { return count; }
    };
  }
}
