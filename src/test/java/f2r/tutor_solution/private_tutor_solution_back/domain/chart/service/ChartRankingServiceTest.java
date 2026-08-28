package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartCriteria;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorRanking;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRankingList;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 순수 로직 + 빈-publication 경로. Mockito 기본 JdbcTemplate 은 목록 조회에 빈 리스트를 돌려주므로
 * "현재 publication 없음" 상태를 그대로 검증할 수 있다. DB 왕복 검증은 {@code ChartRankingMySqlTest}.
 */
class ChartRankingServiceTest {

  private final ChartRankingService service = new ChartRankingService(mock(JdbcTemplate.class));
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  // ---- 평가 기준: 실제 Score 산식과 일치 ----

  @Test
  void criteria_weightsSumTo100_haveSixItems_versionMatches() {
    ChartCriteria c = service.criteria();
    assertThat(c.criteriaVersion()).isEqualTo(ChartSiteToolService.CRITERIA_VERSION);
    assertThat(c.maxScore()).isEqualTo(100);
    assertThat(c.criteria()).hasSize(6);
    assertThat(c.criteria().stream().mapToInt(ChartCriteria.Criterion::weight).sum()).isEqualTo(100);
    assertThat(c.criteria()).extracting(ChartCriteria.Criterion::weight)
        .containsExactly(35, 20, 15, 15, 10, 5);
    assertThat(c.notEvaluated()).anySatisfy(s -> assertThat(s).contains("광고"));
    assertThat(c.notEvaluated()).anySatisfy(s -> assertThat(s).contains("유명세"));
  }

  @Test
  void criteria_weightsMatchScoreImplementationCategoryCaps() {
    // Score 산식이 각 항목에서 낼 수 있는 최대치 = 공개 기준 가중치여야 한다.
    ChartSiteToolService scoreSvc = new ChartSiteToolService(mock(JdbcTemplate.class));
    java.util.Map<String, Object> candidate = new java.util.HashMap<>();
    candidate.put("identity_summary", "정체성");
    candidate.put("education_philosophy_summary", "철학");
    candidate.put("curriculum_summary", "커리큘럼");
    candidate.put("distinctive_teaching_summary", "차별성");
    List<java.util.Map<String, Object>> evidence = List.of(
        ev("TEACHING_METHOD", "2026-01-01", "https://a/1"),
        ev("STUDENT_GUIDANCE", "2026-02-01", "https://a/2"),
        ev("OFFLINE_ACTIVITY", "2026-03-01", "https://a/3"),
        ev("MATERIAL", "2026-04-01", "https://a/4"),
        ev("SUBJECT_EXPERTISE", "2026-05-01", "https://a/5"),
        ev("EDUCATIONAL_CONTENT", "2026-06-01", "https://a/6"));
    java.util.Map<String, Integer> breakdown = scoreSvc.score(candidate, evidence);
    List<Integer> caps = breakdown.values().stream().toList();

    // score() 는 6개 항목을 이 순서로 반환한다: 고유성35 / 실천20 / 지속성15 / 전문성15 / 검증10 / 지역5
    assertThat(caps).containsExactly(35, 20, 15, 15, 10, 5);
    assertThat(service.criteria().criteria()).extracting(ChartCriteria.Criterion::weight)
        .containsExactlyElementsOf(caps);
  }

  private static java.util.Map<String, Object> ev(String kind, String date, String url) {
    return java.util.Map.of("evidence_kind", kind, "activity_date", date, "source_url", url,
        "educationally_informative", true);
  }

  // ---- Ranking disclaimer ----

  @Test
  void rankingDisclaimer_saysNotRealTeachingAbilityRanking() {
    assertThat(ChartRankingService.RANKING_DISCLAIMER)
        .contains("실제 수업 능력 순위가 아닙니다")
        .contains("동일한 BuildIt Score");
  }

  // ---- 빈 publication 경로 ----

  @Test
  void nationwide_whenNoPublication_isEmptyListWithDisclaimer() {
    ChartRankingList list = service.nationwide(null);
    assertThat(list.scope()).isEqualTo("NATIONWIDE");
    assertThat(list.educators()).isEmpty();
    assertThat(list.total()).isZero();
    assertThat(list.disclaimer()).contains("실제 수업 능력 순위가 아닙니다");
  }

  @Test
  void region_requiresProvince() {
    assertThatThrownBy(() -> service.region(null, "강남구")).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.region("  ", null)).isInstanceOf(ResponseStatusException.class);
    assertThat(service.region("서울특별시", "강남구").scope()).isEqualTo("REGION");
  }

  @Test
  void forEducator_whenNotInPublication_returnsUnranked_noScoreFields() throws Exception {
    ChartEducatorRanking r = service.forEducator(12345L);
    assertThat(r.ranked()).isFalse();
    assertThat(r.buildItScore()).isNull();
    assertThat(r.overallRank()).isNull();
    assertThat(r.disclaimer()).contains("실제 수업 능력 순위가 아닙니다");
    // 직렬화에도 순위/점수 값이 없다
    String json = objectMapper.writeValueAsString(r);
    assertThat(json).contains("\"ranked\":false");
  }

  // ---- DTO 개인정보/내부필드 비노출 ----

  @Test
  void rankedEducatorDto_hasNoPrivateOrInternalFields() {
    Set<String> forbidden = Set.of(
        "personKey", "person_key", "sourceUrlHash", "eligibilityReasonsJson", "deepSearchResult",
        "exclusionCheck", "googleQuery", "legacyMetadataJson", "excludedReason", "phone", "email",
        "address", "createdBy", "updatedBy", "scoreBreakdown", "evidenceJson");
    assertThat(recordFields(f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRankedEducator.class))
        .doesNotContainAnyElementsOf(forbidden);
    assertThat(recordFields(ChartEducatorRanking.class)).doesNotContainAnyElementsOf(forbidden);
  }

  private static Set<String> recordFields(Class<?> rec) {
    return Arrays.stream(rec.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
  }
}
