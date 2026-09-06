package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.LatestUpdateItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.repository.WikiLearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LatestUpdatesService — 종류 무관 최신순 병합 + 48시간 NEW 강조")
class LatestUpdatesServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 13, 0);

  private WikiExamOriginalRepository originalRepository;
  private WikiAnalysisMaterialRepository analysisRepository;
  private WikiLearningMaterialRepository learningRepository;
  private LatestUpdatesService service;

  @BeforeEach
  void setUp() {
    originalRepository = mock(WikiExamOriginalRepository.class);
    analysisRepository = mock(WikiAnalysisMaterialRepository.class);
    learningRepository = mock(WikiLearningMaterialRepository.class);
    service = new LatestUpdatesService(originalRepository, analysisRepository, learningRepository,
        Clock.fixed(Instant.parse("2026-09-06T04:00:00Z"), SEOUL));
  }

  @Test
  @DisplayName("세 자료 유형을 createdAt 최신순으로 합치고, 48시간 이내만 isNew=true")
  void latestUpdates_mergedAndSortedWithNewFlag() {
    when(originalRepository.findTop8ByDeletedFalseAndVisibleTrueOrderByCreatedAtDesc())
        .thenReturn(List.of(original(1L, 3, "원본 오래됨", NOW.minusDays(5))));
    when(analysisRepository.findTop8ByDeletedFalseAndVisibleTrueOrderByCreatedAtDesc())
        .thenReturn(List.of(analysis(2L, 2, "분석 방금", NOW.minusHours(1))));
    when(learningRepository.findTop8ByDeletedFalseOrderByCreatedAtDesc())
        .thenReturn(List.of(learning(3L, 1, "학습 어제", NOW.minusHours(30))));

    List<LatestUpdateItem> items = service.latestUpdates();

    assertThat(items).extracting(LatestUpdateItem::title)
        .containsExactly("분석 방금", "학습 어제", "원본 오래됨");
    assertThat(items.get(0).isNew()).isTrue();   // 1시간 전
    assertThat(items.get(1).isNew()).isTrue();   // 30시간 전
    assertThat(items.get(2).isNew()).isFalse();  // 5일 전
    assertThat(items.get(0).href()).isEqualTo("/wiki/analysis");
    assertThat(items.get(1).href()).isEqualTo("/wiki/learning/3");
    assertThat(items.get(2).href()).isEqualTo("/wiki/originals");
  }

  private WikiExamOriginal original(Long id, int grade, String title, LocalDateTime createdAt) {
    WikiExamOriginal m = mock(WikiExamOriginal.class);
    when(m.getWikiExamOriginalId()).thenReturn(id);
    when(m.getGrade()).thenReturn(grade);
    when(m.getTitle()).thenReturn(title);
    when(m.getCreatedAt()).thenReturn(createdAt);
    return m;
  }

  private WikiAnalysisMaterial analysis(Long id, int grade, String title, LocalDateTime createdAt) {
    WikiAnalysisMaterial m = mock(WikiAnalysisMaterial.class);
    when(m.getWikiAnalysisMaterialId()).thenReturn(id);
    when(m.getGrade()).thenReturn(grade);
    when(m.getTitle()).thenReturn(title);
    when(m.getCreatedAt()).thenReturn(createdAt);
    return m;
  }

  private WikiLearningMaterial learning(Long id, int grade, String title, LocalDateTime createdAt) {
    WikiLearningMaterial m = mock(WikiLearningMaterial.class);
    when(m.getWikiLearningMaterialId()).thenReturn(id);
    when(m.getGrade()).thenReturn(grade);
    when(m.getTitle()).thenReturn(title);
    when(m.getCreatedAt()).thenReturn(createdAt);
    return m;
  }
}
