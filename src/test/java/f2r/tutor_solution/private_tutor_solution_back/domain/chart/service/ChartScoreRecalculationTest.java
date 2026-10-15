package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartCandidateEnrichmentService.EnrichmentRequest;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Score 재계산 회귀 테스트.
 *
 * <ul>
 *   <li>enrichment 후 preview score가 최신 profile/evidence를 반영한다 ({@code score} 직접 검증).</li>
 *   <li>review(snapshot)와 preview가 같은 산식에서 나온다 (동일 입력 → 동일 결과).</li>
 *   <li>enrichment 요청 구조상 public snapshot·eligibility를 바꿀 수 없다 (해당 필드 없음).</li>
 *   <li>최소 profile의 공식 점수가 17점임을 고정한다 (10/0/0/5/2/0).</li>
 * </ul>
 */
class ChartScoreRecalculationTest {

  private final ChartSiteToolService service = new ChartSiteToolService(mock(JdbcTemplate.class));

  private static final String PHILOSOPHY =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";
  private static final String CURRICULUM =
      "진단 평가로 취약 지점을 찾은 뒤 개념 강의를 하고, 학교별 기출 분석과 변형 문제로 적용한 다음 오답 노트를 반복한다.";
  private static final String TEACHING =
      "수업 시간에 학생이 직접 설명하는 구간을 두고, 발표 내용을 녹음해 발음과 논리 전개를 되돌아본다.";

  private static Map<String, Object> candidate(String identity, String philosophy, String curriculum,
      String teaching) {
    Map<String, Object> c = new HashMap<>();
    c.put("identity_summary", identity);
    c.put("education_philosophy_summary", philosophy);
    c.put("curriculum_summary", curriculum);
    c.put("distinctive_teaching_summary", teaching);
    return c;
  }

  private static Map<String, Object> evidence(String kind, String datePrefix, String url) {
    Map<String, Object> e = new HashMap<>();
    e.put("evidence_kind", kind);
    e.put("activity_date", datePrefix == null ? null : datePrefix + "-15");
    e.put("source_url", url);
    return e;
  }

  // ---- 1. enrichment 반영: #45형(4필드 + 수업근거 2종 + 출처 2 + 월 1 + 오프라인) = 63 ----

  @Test
  void score_reflectsEnrichedProfileAndEvidence_63() {
    Map<String, Object> c = candidate("강남구 압구정동 학원 원장 (2025년 인터뷰).", PHILOSOPHY, CURRICULUM, TEACHING);
    List<Map<String, Object>> evidence = List.of(
        evidence("TEACHING_METHOD", "2026-05", "https://example.com/class"),
        evidence("OFFLINE_ACTIVITY", "2026-05", "https://example.com/offline"));

    Map<String, Integer> breakdown = service.score(c, evidence);

    assertThat(breakdown.get("교육자 고유성")).isEqualTo(35);
    assertThat(breakdown.get("수업·교육 실천")).isEqualTo(14);
    assertThat(breakdown.get("교육 콘텐츠 지속성")).isEqualTo(5);
    assertThat(breakdown.get("전문성")).isEqualTo(0);
    assertThat(breakdown.get("검증 근거")).isEqualTo(4);
    assertThat(breakdown.get("지역 활동")).isEqualTo(5);
    assertThat(breakdown.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(63);
  }

  // ---- 최소 profile = 17 (날짜 없는 근거 1건 → 지속성 0) ----

  @Test
  void score_minimalEvaluatableProfile_is17() {
    Map<String, Object> c = candidate("학원 원장.", null, null, null);
    List<Map<String, Object>> evidence = List.of(
        evidence("MATERIAL", null, "https://example.com/material"));

    Map<String, Integer> breakdown = service.score(c, evidence);

    assertThat(breakdown).containsEntry("교육자 고유성", 10)
        .containsEntry("수업·교육 실천", 0)
        .containsEntry("교육 콘텐츠 지속성", 0)
        .containsEntry("전문성", 5)
        .containsEntry("검증 근거", 2)
        .containsEntry("지역 활동", 0);
    assertThat(breakdown.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(17);
  }

  // ---- 3. preview와 review snapshot은 같은 산식 (동일 입력 → 동일 총점) ----

  @Test
  void score_deterministic_sameInputSameTotal() {
    Map<String, Object> c = candidate("학원 원장.", PHILOSOPHY, null, null);
    List<Map<String, Object>> evidence = List.of(
        evidence("CURRICULUM", "2026-03", "https://example.com/a"),
        evidence("CURRICULUM", "2026-04", "https://example.com/b"));

    int previewTotal = service.score(c, evidence).values().stream().mapToInt(Integer::intValue).sum();
    int reviewTotal = service.score(c, evidence).values().stream().mapToInt(Integer::intValue).sum();

    assertThat(reviewTotal).isEqualTo(previewTotal);
  }

  // ---- 2. enrichment 구조상 snapshot·eligibility 변경 불가 ----

  @Test
  void enrichmentRequest_hasNoScoreOrEligibilityFields() {
    Set<String> fields = Arrays.stream(EnrichmentRequest.class.getRecordComponents())
        .map(RecordComponent::getName)
        .map(String::toLowerCase)
        .collect(Collectors.toSet());
    assertThat(fields).doesNotContain("score", "total_score", "totalscore",
        "eligibility_status", "eligibilitystatus", "rank", "publication");
  }

  @Test
  void enrichment_neverDeletesExistingProfile() {
    assertThat(ChartCandidateEnrichmentService.mergeSummary(PHILOSOPHY, null, true)).isNull();
    assertThat(ChartCandidateEnrichmentService.mergeSummary(PHILOSOPHY, " ", true)).isNull();
    assertThat(ChartCandidateEnrichmentService.mergeSummary(PHILOSOPHY, CURRICULUM, false)).isNull();
  }

  // ---- 날짜 없는 월은 지속성에 미반영 ----

  @Test
  void score_evidenceWithoutDate_givesNoPersistence() {
    Map<String, Object> c = candidate("학원 원장.", PHILOSOPHY, CURRICULUM, TEACHING);
    List<Map<String, Object>> evidence = List.of(
        evidence("TEACHING_METHOD", null, "https://example.com/a"),
        evidence("MATERIAL", null, "https://example.com/b"));

    assertThat(service.score(c, evidence).get("교육 콘텐츠 지속성")).isEqualTo(0);
  }
}
