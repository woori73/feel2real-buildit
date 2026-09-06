package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository.PopularDownloadRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.PopularDownloadItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.PopularDownloads;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.support.WikiMaterialRef;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.repository.WikiLearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 Wiki 메인 "🔥 지금 많이 받는 자료 TOP 10".
 *
 * <p>Single Source of Truth = 기존 Analytics {@code wiki_download_logs} (별도 카운터/테이블 없음).
 * 기본 집계 기간은 최근 24시간이며, 그 안의 다운로드가 너무 적으면({@link #MIN_PRIMARY_DOWNLOADS} 미만)
 * 최근 7일로 fallback 한다 — 화면에 표시하는 기간 라벨과 실제 집계 기간은 항상 일치한다.
 *
 * <p>순위 변동(↑/↓/NEW)은 <b>현재 창</b> vs <b>바로 직전 동일 길이 창</b>의 순위를 비교해 계산한다
 * (스냅샷 테이블 없음). 학년은 로그에 없으므로 원본 자료 테이블에서 <b>벌크</b>로 조회한다(N+1 없음).
 *
 * <p>사용자 증가에 대비해 결과는 {@link #cacheTtl}(기본 60초) 동안 in-memory 캐시한다. 외부 캐시 인프라는
 * 도입하지 않는다(프로젝트에 없음). 다운로드 → Analytics 기록 → 최대 약 1분 내 순위 반영.
 */
@Service
@Transactional(readOnly = true)
public class PopularDownloadService {

  static final int TOP_N = 10;
  static final long MIN_PRIMARY_DOWNLOADS = 10;

  private final WikiDownloadLogRepository downloadRepository;
  private final WikiExamOriginalRepository originalRepository;
  private final WikiAnalysisMaterialRepository analysisRepository;
  private final WikiLearningMaterialRepository learningRepository;
  private final Clock clock;
  private final Duration cacheTtl;
  private final AtomicReference<Cached> cache = new AtomicReference<>();

  @Autowired
  public PopularDownloadService(
      WikiDownloadLogRepository downloadRepository,
      WikiExamOriginalRepository originalRepository,
      WikiAnalysisMaterialRepository analysisRepository,
      WikiLearningMaterialRepository learningRepository) {
    this(downloadRepository, originalRepository, analysisRepository, learningRepository,
        Clock.system(ZoneId.of("Asia/Seoul")), Duration.ofSeconds(60));
  }

  PopularDownloadService(
      WikiDownloadLogRepository downloadRepository,
      WikiExamOriginalRepository originalRepository,
      WikiAnalysisMaterialRepository analysisRepository,
      WikiLearningMaterialRepository learningRepository,
      Clock clock,
      Duration cacheTtl) {
    this.downloadRepository = downloadRepository;
    this.originalRepository = originalRepository;
    this.analysisRepository = analysisRepository;
    this.learningRepository = learningRepository;
    this.clock = clock;
    this.cacheTtl = cacheTtl;
  }

  public PopularDownloads getPopularDownloads() {
    Cached current = cache.get();
    Instant now = clock.instant();
    if (current != null && Duration.between(current.generatedAt(), now).compareTo(cacheTtl) < 0) {
      return current.value();
    }
    PopularDownloads fresh = compute();
    cache.set(new Cached(now, fresh));
    return fresh;
  }

  PopularDownloads compute() {
    LocalDateTime now = LocalDateTime.now(clock);

    Window window = new Window(now.minusHours(24), now, "최근 24시간", 24);
    if (downloadRepository.countBetween(window.from(), window.to()) < MIN_PRIMARY_DOWNLOADS) {
      window = new Window(now.minusDays(7), now, "최근 7일", 24 * 7);
    }

    List<PopularDownloadRow> currentRows =
        downloadRepository.aggregateBetween(window.from(), window.to(), PageRequest.of(0, TOP_N));

    Duration length = Duration.between(window.from(), window.to());
    Map<String, Integer> previousRank = rankMap(downloadRepository.aggregateBetween(
        window.from().minus(length), window.from(), PageRequest.of(0, TOP_N)));

    Map<Long, Integer> grades = resolveGrades(currentRows);

    List<PopularDownloadItem> items = new ArrayList<>(currentRows.size());
    for (int i = 0; i < currentRows.size(); i++) {
      PopularDownloadRow row = currentRows.get(i);
      int rank = i + 1;
      WikiMaterialCategory category = WikiMaterialCategory.valueOf(row.getCategory());
      Integer previous = previousRank.get(identity(row.getCategory(), row.getMaterialId()));
      Trend trend = trendOf(previous, rank);
      items.add(new PopularDownloadItem(
          rank,
          row.getMaterialId(),
          category.name(),
          WikiMaterialRef.label(category),
          grades.get(row.getMaterialId()),
          row.getTitle(),
          row.getDownloadCount(),
          trend.name,
          trend.amount,
          WikiMaterialRef.href(category, row.getMaterialId())));
    }
    return new PopularDownloads(window.label(), window.hours(), items);
  }

  private Map<String, Integer> rankMap(List<PopularDownloadRow> rows) {
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < rows.size(); i++) {
      map.put(identity(rows.get(i).getCategory(), rows.get(i).getMaterialId()), i + 1);
    }
    return map;
  }

  private String identity(String category, Long materialId) {
    return category + ":" + materialId;
  }

  private Trend trendOf(Integer previousRank, int currentRank) {
    if (previousRank == null) {
      return new Trend("NEW", 0);
    }
    if (previousRank == currentRank) {
      return new Trend("SAME", 0);
    }
    return previousRank > currentRank
        ? new Trend("UP", previousRank - currentRank)
        : new Trend("DOWN", currentRank - previousRank);
  }

  /** 카테고리별로 materialId 를 모아 한 번씩만 조회한다(N+1 방지). 없는 자료는 학년 null. */
  private Map<Long, Integer> resolveGrades(List<PopularDownloadRow> rows) {
    Map<WikiMaterialCategory, List<Long>> idsByCategory = rows.stream().collect(Collectors.groupingBy(
        r -> WikiMaterialCategory.valueOf(r.getCategory()),
        Collectors.mapping(PopularDownloadRow::getMaterialId, Collectors.toList())));

    Map<Long, Integer> grades = new HashMap<>();
    List<Long> originalIds = idsByCategory.get(WikiMaterialCategory.ORIGINAL);
    if (originalIds != null) {
      originalRepository.findAllById(originalIds)
          .forEach(m -> grades.put(m.getWikiExamOriginalId(), m.getGrade()));
    }
    List<Long> analysisIds = idsByCategory.get(WikiMaterialCategory.ANALYSIS);
    if (analysisIds != null) {
      analysisRepository.findAllById(analysisIds)
          .forEach(m -> grades.put(m.getWikiAnalysisMaterialId(), m.getGrade()));
    }
    List<Long> learningIds = idsByCategory.get(WikiMaterialCategory.LEARNING);
    if (learningIds != null) {
      learningRepository.findAllById(learningIds)
          .forEach(m -> grades.put(m.getWikiLearningMaterialId(), m.getGrade()));
    }
    return grades;
  }

  private record Window(LocalDateTime from, LocalDateTime to, String label, int hours) {}

  private record Cached(Instant generatedAt, PopularDownloads value) {}

  private record Trend(String name, int amount) {}
}
