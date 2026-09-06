package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.LatestUpdateItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.support.WikiMaterialRef;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.repository.WikiLearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 Wiki 메인 "NEW 최신 업데이트" — 최근에 올라온 자료(모의고사 원본 · 분석 자료 · 모의고사 학습 자료)를
 * 종류 무관 최신순으로 합쳐서 상위 {@link #FEED_SIZE}개. 하드코딩 없음 — 자료의 {@code createdAt} 으로 자동 산출.
 * 최근 {@link #NEW_WINDOW}(48시간) 이내 자료만 {@code isNew} 강조 대상이다.
 */
@Service
@Transactional(readOnly = true)
public class LatestUpdatesService {

  static final int FEED_SIZE = 8;
  static final Duration NEW_WINDOW = Duration.ofHours(48);

  private final WikiExamOriginalRepository originalRepository;
  private final WikiAnalysisMaterialRepository analysisRepository;
  private final WikiLearningMaterialRepository learningRepository;
  private final Clock clock;

  @Autowired
  public LatestUpdatesService(
      WikiExamOriginalRepository originalRepository,
      WikiAnalysisMaterialRepository analysisRepository,
      WikiLearningMaterialRepository learningRepository) {
    this(originalRepository, analysisRepository, learningRepository,
        Clock.system(ZoneId.of("Asia/Seoul")));
  }

  LatestUpdatesService(
      WikiExamOriginalRepository originalRepository,
      WikiAnalysisMaterialRepository analysisRepository,
      WikiLearningMaterialRepository learningRepository,
      Clock clock) {
    this.originalRepository = originalRepository;
    this.analysisRepository = analysisRepository;
    this.learningRepository = learningRepository;
    this.clock = clock;
  }

  public List<LatestUpdateItem> latestUpdates() {
    LocalDateTime newThreshold = LocalDateTime.now(clock).minus(NEW_WINDOW);
    List<LatestUpdateItem> all = new ArrayList<>();

    for (var m : originalRepository.findTop8ByDeletedFalseAndVisibleTrueOrderByCreatedAtDesc()) {
      all.add(item(WikiMaterialCategory.ORIGINAL, m.getWikiExamOriginalId(), m.getGrade(),
          m.getTitle(), m.getCreatedAt(), newThreshold));
    }
    for (var m : analysisRepository.findTop8ByDeletedFalseAndVisibleTrueOrderByCreatedAtDesc()) {
      all.add(item(WikiMaterialCategory.ANALYSIS, m.getWikiAnalysisMaterialId(), m.getGrade(),
          m.getTitle(), m.getCreatedAt(), newThreshold));
    }
    for (var m : learningRepository.findTop8ByDeletedFalseOrderByCreatedAtDesc()) {
      all.add(item(WikiMaterialCategory.LEARNING, m.getWikiLearningMaterialId(), m.getGrade(),
          m.getTitle(), m.getCreatedAt(), newThreshold));
    }

    all.sort(Comparator.comparing(
        LatestUpdateItem::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
    return all.size() > FEED_SIZE ? all.subList(0, FEED_SIZE) : all;
  }

  private LatestUpdateItem item(WikiMaterialCategory category, Long id, Integer grade, String title,
      LocalDateTime createdAt, LocalDateTime newThreshold) {
    boolean isNew = createdAt != null && createdAt.isAfter(newThreshold);
    return new LatestUpdateItem(
        category.name(), WikiMaterialRef.label(category), grade, title, createdAt, isNew,
        WikiMaterialRef.href(category, id));
  }
}
