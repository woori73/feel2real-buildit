package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.Assessment;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.DraftRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.EvidenceRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.ReevaluationDecision;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Eligibility re-evaluation 회귀 테스트 (PHASE 2).
 *
 * <ul>
 *   <li>최초 판정과 동일한 {@code assess} 기준을 재사용한다 (로직 복사 없음).</li>
 *   <li>PASS일 때만 RESEARCH_PENDING → EVALUATABLE 승격 대상이 된다.</li>
 *   <li>FAIL이면 RESEARCH_PENDING을 유지한다.</li>
 *   <li>dryRun은 어떤 UPDATE도 실행하지 않는다.</li>
 *   <li>승격 시에도 status·reasons·updated_at 세 컬럼만 변경한다 (profile/evidence 불변).</li>
 *   <li>enrichment만으로 자동 승격되지 않는다 (승격 경로는 re-evaluation뿐).</li>
 * </ul>
 */
class ChartEligibilityReevaluationTest {

  private final ChartSiteToolService service = new ChartSiteToolService(mock(JdbcTemplate.class));

  private static final String PHILOSOPHY =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";
  private static final String CURRICULUM =
      "진단 평가로 취약 지점을 찾은 뒤 개념 강의를 하고, 학교별 기출 분석과 변형 문제로 적용한 다음 오답 노트를 반복한다.";
  private static final String TEACHING =
      "수업 시간에 학생이 직접 설명하는 구간을 두고, 발표 내용을 녹음해 발음과 논리 전개를 되돌아본다.";

  private static EvidenceRequest evidence(String kind, boolean currentYear, boolean informative) {
    LocalDate date = currentYear ? LocalDate.now() : LocalDate.now().minusYears(1);
    return new EvidenceRequest("수업", "수업 근거 요약", "제목", "https://example.com/" + kind.toLowerCase(),
        date, kind, informative);
  }

  private static DraftRequest draft(String philosophy, String curriculum, List<EvidenceRequest> evidence) {
    return new DraftRequest("SEOUL-GANGNAM", "홍길동", "원장", "테스트학원", "서울특별시", "강남구",
        "강남구 학원 원장.", philosophy, curriculum, TEACHING, "최근 특강을 진행함.",
        List.of(), List.of(), null, null, null, null, null, null, "테스트",
        true, true, true, true, List.of(), evidence);
  }

  // ---- 6. PASS → 승격 대상 (#100형: 철학·커리큘럼 + 당해 OFFLINE 근거) ----

  @Test
  void decideReevaluation_passPromotes() {
    Assessment assessment = service.assess(draft(PHILOSOPHY, CURRICULUM, List.of(
        evidence("OFFLINE_ACTIVITY", true, true),
        evidence("CURRICULUM", true, true))));

    assertThat(assessment.evaluatable()).isTrue();

    ReevaluationDecision decision = ChartSiteToolService.decideReevaluation(
        "RESEARCH_PENDING", false, assessment);
    assertThat(decision.promote()).isTrue();
    assertThat(decision.before()).isEqualTo("RESEARCH_PENDING");
    assertThat(decision.after()).isEqualTo("EVALUATABLE");
    assertThat(decision.reasons()).isNotEmpty();
  }

  // ---- 7. FAIL → 유지: 철학 없음 (#62·#91·#93·#96·#101형) ----

  @Test
  void decideReevaluation_missingPhilosophyStays() {
    Assessment assessment = service.assess(draft(null, CURRICULUM, List.of(
        evidence("OFFLINE_ACTIVITY", true, true))));

    assertThat(assessment.researchPending()).isTrue();

    ReevaluationDecision decision = ChartSiteToolService.decideReevaluation(
        "RESEARCH_PENDING", false, assessment);
    assertThat(decision.promote()).isFalse();
    assertThat(decision.after()).isEqualTo("RESEARCH_PENDING");
    assertThat(decision.reasons()).isNotEmpty();
  }

  // ---- 7. FAIL → 유지: 당해 근거 없음 (#93·#101·#102·#108·#109형) ----

  @Test
  void decideReevaluation_staleEvidenceOnlyStays() {
    Assessment assessment = service.assess(draft(PHILOSOPHY, CURRICULUM, List.of(
        evidence("OFFLINE_ACTIVITY", false, true))));

    assertThat(assessment.researchPending()).isTrue();

    ReevaluationDecision decision = ChartSiteToolService.decideReevaluation(
        "RESEARCH_PENDING", false, assessment);
    assertThat(decision.promote()).isFalse();
    assertThat(decision.after()).isEqualTo("RESEARCH_PENDING");
  }

  // ---- 제외 후보는 승격 불가 / 이미 EVALUATABLE이면 no-op ----

  @Test
  void decideReevaluation_excludedOrAlreadyEvaluatableNeverPromotes() {
    Assessment pass = service.assess(draft(PHILOSOPHY, CURRICULUM, List.of(
        evidence("OFFLINE_ACTIVITY", true, true))));

    ReevaluationDecision excluded =
        ChartSiteToolService.decideReevaluation("RESEARCH_PENDING", true, pass);
    assertThat(excluded.promote()).isFalse();

    ReevaluationDecision already =
        ChartSiteToolService.decideReevaluation("EVALUATABLE", false, pass);
    assertThat(already.promote()).isFalse();
    assertThat(already.after()).isEqualTo("EVALUATABLE");
  }

  // ---- dryRun은 UPDATE 없음 ----

  @Test
  void reevaluateEligibility_dryRunPerformsNoUpdate() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ChartSiteToolService svc = new ChartSiteToolService(jdbc);
    stubStoredCandidate(jdbc, pendingRow(), List.of(activity("OFFLINE_ACTIVITY", true)));

    Map<String, Object> result = svc.reevaluateEligibility(100L, true);

    assertThat(result.get("dryRun")).isEqualTo(true);
    assertThat(result.get("applied")).isEqualTo(false);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  // ---- 승격 시 status 3컬럼만 변경 (profile/evidence 컬럼 없음) ----

  @Test
  void reevaluateEligibility_promoteUpdatesOnlyStatusColumns() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ChartSiteToolService svc = new ChartSiteToolService(jdbc);
    stubStoredCandidate(jdbc, pendingRow(), List.of(activity("OFFLINE_ACTIVITY", true)));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    Map<String, Object> result = svc.reevaluateEligibility(100L, false);

    assertThat(result.get("promote")).isEqualTo(true);
    assertThat(result.get("applied")).isEqualTo(true);
    assertThat(result.get("after")).isEqualTo("EVALUATABLE");
    var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sqlCaptor.capture(), any(Object[].class));
    String sql = sqlCaptor.getValue();
    assertThat(sql).contains("eligibility_status");
    assertThat(sql).doesNotContain("education_philosophy_summary");
    assertThat(sql).doesNotContain("curriculum_summary");
    assertThat(sql).doesNotContain("chart_candidate_activities");
  }

  // ---- FAIL이면 dryRun=false여도 UPDATE 없음 (5. enrichment만으로 공개 불가) ----

  @Test
  void reevaluateEligibility_failPerformsNoUpdateEvenWhenNotDryRun() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ChartSiteToolService svc = new ChartSiteToolService(jdbc);
    Map<String, Object> row = pendingRow();
    row.put("education_philosophy_summary", null);
    stubStoredCandidate(jdbc, row, List.of(activity("OFFLINE_ACTIVITY", true)));

    Map<String, Object> result = svc.reevaluateEligibility(62L, false);

    assertThat(result.get("promote")).isEqualTo(false);
    assertThat(result.get("applied")).isEqualTo(false);
    assertThat(result.get("after")).isEqualTo("RESEARCH_PENDING");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  // ---- fixture ----

  private static Map<String, Object> pendingRow() {
    Map<String, Object> row = new HashMap<>();
    row.put("candidate_id", 100L);
    row.put("zone_id", "GYEONGGIAM-POHANG");
    row.put("name", "강수민");
    row.put("role", "원장");
    row.put("academy_name", "링키영어");
    row.put("activity_province", "경상북도");
    row.put("activity_city_district", "포항시");
    row.put("identity_summary", "포항시 학원 원장.");
    row.put("education_philosophy_summary", PHILOSOPHY);
    row.put("curriculum_summary", CURRICULUM);
    row.put("distinctive_teaching_summary", TEACHING);
    row.put("recent_activity_summary", "최근 특강을 진행함.");
    row.put("strengths_json", "[]");
    row.put("improvement_suggestions_json", "[]");
    row.put("potential_signals_json", null);
    row.put("profile_image_url", null);
    row.put("profile_image_source_url", null);
    row.put("official_homepage", null);
    row.put("official_blog", null);
    row.put("official_youtube", null);
    row.put("official_social", null);
    row.put("offline_activity_confirmed", 1);
    row.put("current_year_activity_confirmed", 1);
    row.put("educator_identity_confirmed", 1);
    row.put("exclusion_free", 1);
    row.put("eligibility_status", "RESEARCH_PENDING");
    row.put("excluded_from_chart", 0);
    row.put("legacy_source", null);
    return row;
  }

  private static Map<String, Object> activity(String kind, boolean currentYear) {
    Map<String, Object> a = new HashMap<>();
    a.put("content_type", "수업");
    a.put("content_summary", "수업 근거 요약");
    a.put("source_url", "https://example.com/" + kind.toLowerCase());
    a.put("activity_date", currentYear ? LocalDate.now() : LocalDate.now().minusYears(1));
    a.put("evidence_kind", kind);
    a.put("educationally_informative", 1);
    return a;
  }

  @SuppressWarnings("unchecked")
  private static void stubStoredCandidate(JdbcTemplate jdbc, Map<String, Object> row,
      List<Map<String, Object>> activities) {
    when(jdbc.queryForList(anyString(), anyLong())).thenAnswer(invocation -> {
      Object firstArg = invocation.getArgument(0);
      String sql = String.valueOf(firstArg);
      if (sql.contains("chart_candidate_activities")) {
        return activities;
      }
      return List.of(row);
    });
  }
}
