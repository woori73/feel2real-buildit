package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartCandidateEnrichmentService.EnrichmentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartCandidateEnrichmentService.EvidenceInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 기존 교육자 보완(Enrichment) E2E (실제 MySQL). CI(build-check)에서는 실행하지 않는다.
 *
 * <p>서울 legacy 41명처럼 EVALUATABLE·요약 필드 비어 있는 기존 candidate 를 대상으로:
 * <ol>
 *   <li>보완 성공 / candidate 수 불변 / candidate_id 유지</li>
 *   <li>philosophy·curriculum·teaching 요약 보완, Evidence activity 추가, 기존 Evidence 보존</li>
 *   <li>Score·eligibility_status·publication·identity 불변</li>
 *   <li>null/blank 로 기존 데이터 삭제 불가, 빈 요청 거부, 없는 candidate_id 거부</li>
 * </ol>
 */
@SpringBootTest
@Transactional
class ChartCandidateEnrichmentMySqlTest {

  @Autowired ChartCandidateEnrichmentService service;
  @Autowired ChartSiteToolService siteToolService;
  @Autowired JdbcTemplate jdbcTemplate;

  private static final String PHILOSOPHY =
      "학생마다 학습 속도가 다르다는 전제에서 개별 진도와 반복 학습으로 스스로 이해에 도달하도록 돕는다.";
  private static final String CURRICULUM =
      "진단으로 취약 지점을 찾고 개념 강의 후 학교별 기출 분석과 변형 문제로 적용한 뒤 오답을 반복한다.";
  private static final String TEACHING = "학교별 내신 지문을 직접 분석해 수준별로 지도한다.";

  private String tag;

  @BeforeEach
  void setUp() {
    tag = "enr" + System.nanoTime();
  }

  /** 서울 legacy 41명과 같은 형태: EVALUATABLE, legacy_source 보유, 요약 필드 전부 NULL, Evidence 1건. */
  private long insertLegacyCandidate() {
    String personKey = tag + "|legacy";
    jdbcTemplate.update("""
        INSERT INTO chart_candidates
        (zone_id, person_key, name, role, academy_name, activity_region, activity_province,
         activity_city_district, content_type, content_summary, identity_summary,
         eligibility_status, legacy_source, legacy_metadata_json, excluded_from_chart,
         research_source, criteria_version, source_url, source_url_hash, source_title,
         discovery_reason, deep_search_result, exclusion_check, verification_status, status,
         google_query, discovered_at, created_at, updated_at)
        VALUES (?, ?, '김레거시', '원장', '레거시영어학원', '서울 금천구 시흥동', '서울특별시',
                '금천구', '글', '레거시 조사 당시 요약', '금천구에서 학생을 직접 지도하는 개인 교육자',
                'EVALUATABLE', 'V3_AUTO', '{"v3Score": 2.0, "v3ScoreIsCurrent": false}', 0,
                'LEGACY_V3', 'buildit-chart-v1',
                'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIabc', ?, 'Legacy V3 조사 근거',
                'Legacy V3 선정 이력 승계', '', 'VERIFIED', 'DISCOVERED',
                'legacy-v3-import', 'legacy-v3-google', NOW(6), NOW(6), NOW(6))
        """, tag, personKey, tag + "hash");
    Long id = jdbcTemplate.queryForObject(
        "SELECT candidate_id FROM chart_candidates WHERE person_key=?", Long.class, personKey);
    jdbcTemplate.update("""
        INSERT INTO chart_candidate_activities
        (candidate_id, content_type, content_summary, source_url, evidence_kind,
         educationally_informative, researched_at, discovered_at, created_at, updated_at)
        VALUES (?, '글', '레거시 근거', 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIabc',
                'EDUCATIONAL_CONTENT', b'1', NOW(6), NOW(6), NOW(6), NOW(6))
        """, id);
    return id;
  }

  private Map<String, Object> row(long id) {
    return jdbcTemplate.queryForList("SELECT * FROM chart_candidates WHERE candidate_id=?", id).get(0);
  }

  private int activityCount(long id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_candidate_activities WHERE candidate_id=?", Integer.class, id);
  }

  @SuppressWarnings("unchecked")
  private static List<String> changed(Map<String, Object> result) {
    return (List<String>) result.get("changedFields");
  }

  private EnrichmentRequest request(String philosophy, String curriculum, String teaching,
      List<String> strengths, boolean overwrite, List<EvidenceInput> evidence) {
    return new EnrichmentRequest(philosophy, curriculum, teaching, null, strengths, null, overwrite, evidence);
  }

  private EvidenceInput evidence(String url) {
    return new EvidenceInput("블로그 글", "학교별 내신 분석과 수업 단계를 직접 설명", url,
        LocalDate.of(2026, 3, 1), "CURRICULUM", true);
  }

  // ----- 1) 성공 · candidate 수 불변 · id 유지 -----------------------------------------------

  @Test
  void enrich_fillsEmptySummaries_andAddsEvidence_withoutCreatingOrLosingRows() {
    long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_candidates", Long.class);
    long id = insertLegacyCandidate();

    Map<String, Object> result = service.enrich(id, request(PHILOSOPHY, CURRICULUM, TEACHING,
        List.of("학교별 내신 분석"), false, List.of(evidence("https://blog.naver.com/legacy/223849189138"))));

    // candidate 수: insert 1건 외 증가 없음 (보완은 UPDATE)
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_candidates", Long.class))
        .isEqualTo(before + 1);
    assertThat(result).containsEntry("candidateId", id);
    assertThat(changed(result))
        .contains("education_philosophy_summary", "curriculum_summary", "distinctive_teaching_summary",
            "strengths_json");
    assertThat(result).containsEntry("evidenceAdded", 1).containsEntry("eligibilityStatus", "EVALUATABLE");

    Map<String, Object> after = row(id);
    assertThat(after.get("education_philosophy_summary")).isEqualTo(PHILOSOPHY);
    assertThat(after.get("curriculum_summary")).isEqualTo(CURRICULUM);
    assertThat(after.get("distinctive_teaching_summary")).isEqualTo(TEACHING);
    assertThat(String.valueOf(after.get("strengths_json"))).contains("학교별 내신 분석");
    // 기존 Evidence 보존 + 새 Evidence 추가 = 2건
    assertThat(activityCount(id)).isEqualTo(2);
  }

  // ----- 2) merge: 기존 값 보존 / overwrite -----------------------------------------------------

  @Test
  void enrich_doesNotOverwriteExistingSummary_unlessOverwriteTrue() {
    long id = insertLegacyCandidate();
    service.enrich(id, request(PHILOSOPHY, null, null, null, false, List.of()));

    // overwrite=false → 기존 철학 유지, 변경된 필드 없음
    Map<String, Object> r2 = service.enrich(id, request("다른 철학 설명 문장입니다 하나 둘", null, null, null, false, List.of()));
    assertThat(changed(r2)).doesNotContain("education_philosophy_summary");
    assertThat(row(id).get("education_philosophy_summary")).isEqualTo(PHILOSOPHY);

    // overwrite=true → 교체
    String better = "새 근거로 확인된 더 구체적인 교육철학 설명 문장입니다 하나 둘 셋 넷.";
    Map<String, Object> r3 = service.enrich(id, request(better, null, null, null, true, List.of()));
    assertThat(changed(r3)).contains("education_philosophy_summary");
    assertThat(row(id).get("education_philosophy_summary")).isEqualTo(better);
  }

  // ----- 3) null/blank 로 기존 데이터 삭제 불가 -----------------------------------------------

  @Test
  void enrich_neverDeletesExistingDataWithNullOrBlank() {
    long id = insertLegacyCandidate();
    service.enrich(id, request(PHILOSOPHY, CURRICULUM, null, List.of("강점A"), false,
        List.of(evidence("https://blog.naver.com/legacy/1"))));

    // 이후 blank 요약 + 빈 리스트 + 근거만 추가
    service.enrich(id, new EnrichmentRequest("  ", "", null, null, List.of(), List.of(), true,
        List.of(evidence("https://blog.naver.com/legacy/2"))));

    Map<String, Object> after = row(id);
    assertThat(after.get("education_philosophy_summary")).isEqualTo(PHILOSOPHY);
    assertThat(after.get("curriculum_summary")).isEqualTo(CURRICULUM);
    assertThat(String.valueOf(after.get("strengths_json"))).contains("강점A");
  }

  @Test
  void enrich_rejectsEmptyRequest() {
    long id = insertLegacyCandidate();
    assertThatThrownBy(() -> service.enrich(id, EnrichmentRequest.empty()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("보완할 내용이 없습니다");
  }

  @Test
  void enrich_rejectsUnknownCandidateId() {
    assertThatThrownBy(() -> service.enrich(999_000_111L,
        request(PHILOSOPHY, null, null, null, false, List.of())))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("찾을 수 없습니다");
  }

  // ----- 4) Evidence: 원 출처만, 누적, 중복 무시 --------------------------------------------

  @Test
  void enrich_rejectsNonOriginalEvidenceUrl_andDedupes() {
    long id = insertLegacyCandidate();
    assertThatThrownBy(() -> service.enrich(id, request(null, null, null, null, false,
        List.of(evidence("https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZInew")))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("원 출처");

    // 같은 URL 을 두 번 추가 → 두 번째는 중복으로 무시
    service.enrich(id, request(null, null, null, null, false, List.of(evidence("https://blog.naver.com/legacy/9"))));
    Map<String, Object> r = service.enrich(id, request(null, null, null, null, false,
        List.of(evidence("https://blog.naver.com/legacy/9"))));
    assertThat(r).containsEntry("evidenceAdded", 0).containsEntry("evidenceSkippedDuplicate", 1);
  }

  // ----- 5) Score / eligibility / publication / identity 불변 --------------------------------

  @Test
  void enrich_leavesScoreEligibilityPublicationAndIdentityUnchanged() {
    long scoreSnapshotsBefore = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_score_snapshots", Long.class);
    long publicationsBefore = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publications", Long.class);
    long publicationEntriesBefore = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries", Long.class);

    long id = insertLegacyCandidate();
    Map<String, Object> before = row(id);

    service.enrich(id, request(PHILOSOPHY, CURRICULUM, TEACHING, List.of("강점"), true,
        List.of(evidence("https://blog.naver.com/legacy/223999"))));

    Map<String, Object> after = row(id);
    assertThat(after.get("eligibility_status")).isEqualTo("EVALUATABLE");
    assertThat(after.get("legacy_source")).isEqualTo(before.get("legacy_source"));
    assertThat(after.get("excluded_from_chart")).isEqualTo(before.get("excluded_from_chart"));
    assertThat(after.get("name")).isEqualTo("김레거시");
    assertThat(after.get("academy_name")).isEqualTo("레거시영어학원");
    assertThat(after.get("activity_region")).isEqualTo(before.get("activity_region"));
    assertThat(after.get("activity_province")).isEqualTo("서울특별시");
    assertThat(after.get("activity_city_district")).isEqualTo("금천구");
    assertThat(after.get("person_key")).isEqualTo(before.get("person_key"));
    assertThat(after.get("zone_id")).isEqualTo(before.get("zone_id"));
    assertThat(after.get("status")).isEqualTo(before.get("status"));

    // 보완은 Score snapshot / publication 을 만들지 않는다
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_score_snapshots", Long.class))
        .isEqualTo(scoreSnapshotsBefore);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_publications", Long.class))
        .isEqualTo(publicationsBefore);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_publication_entries", Long.class))
        .isEqualTo(publicationEntriesBefore);
  }

  // ----- 6) 부분 보완: 철학만 확인된 경우 철학만 -------------------------------------------

  @Test
  void enrich_partialUpdate_onlyConfirmedFields() {
    long id = insertLegacyCandidate();
    Map<String, Object> result = service.enrich(id,
        request(PHILOSOPHY, null, null, null, false, List.of()));

    assertThat(changed(result)).containsExactly("education_philosophy_summary");
    Map<String, Object> after = row(id);
    assertThat(after.get("education_philosophy_summary")).isEqualTo(PHILOSOPHY);
    assertThat(after.get("curriculum_summary")).isNull();
    assertThat(after.get("distinctive_teaching_summary")).isNull();
  }

  // ----- 7) 저장 → 즉시 재조회 흐름 (프론트 자동 반영 계약) ---------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void enrich_returnsSavedTrueWithRereadResearch_andConflictList() {
    long id = insertLegacyCandidate();

    // 첫 보완: 빈 필드 채움 + Evidence 1건 → saved:true, conflicts 비어 있음, research가 저장값 그대로
    Map<String, Object> r1 = service.enrich(id, request(PHILOSOPHY, CURRICULUM, TEACHING,
        List.of("강점A"), false, List.of(evidence("https://blog.naver.com/legacy/31"))));
    assertThat(r1).containsEntry("saved", true);
    assertThat((List<String>) r1.get("conflicts")).isEmpty();

    Map<String, Object> research = (Map<String, Object>) r1.get("research");
    assertThat(research).containsEntry("educationPhilosophySummary", PHILOSOPHY);
    assertThat(research).containsEntry("curriculumSummary", CURRICULUM);
    assertThat(research).containsEntry("distinctiveTeachingSummary", TEACHING);
    assertThat((List<String>) research.get("strengths")).contains("강점A");
    assertThat((List<Map<String, Object>>) research.get("evidence"))
        .anyMatch(e -> "https://blog.naver.com/legacy/31".equals(e.get("sourceUrl")));

    // overwrite=false + 기존값과 다른 값 → 저장 대신 conflicts 표시, 기존값 유지
    Map<String, Object> r2 = service.enrich(id,
        request("충돌을 만드는 다른 철학 문장입니다", null, null, null, false, List.of()));
    assertThat((List<String>) r2.get("conflicts")).containsExactly("education_philosophy_summary");
    assertThat(row(id).get("education_philosophy_summary")).isEqualTo(PHILOSOPHY);

    // getResearch 재조회(새로고침 경로) → 동일 값 유지
    Map<String, Object> reloaded = service.getResearch(id);
    assertThat(reloaded).containsEntry("educationPhilosophySummary", PHILOSOPHY);
    assertThat(reloaded).containsEntry("eligibilityStatus", "EVALUATABLE");
    assertThat((List<Map<String, Object>>) reloaded.get("evidence"))
        .anyMatch(e -> "https://blog.naver.com/legacy/31".equals(e.get("sourceUrl")));
  }

  // ----- 8) 저장 후 score/preview 가 새 Evidence 를 반영 ------------------------------------

  @Test
  void previewScore_reflectsNewEvidenceAfterEnrichment() {
    long id = insertLegacyCandidate();
    Map<String, Object> before = siteToolService.previewScore(id);
    int beforeEvidence = ((Number) before.get("evidenceCount")).intValue();

    service.enrich(id, request(null, null, null, null, false,
        List.of(evidence("https://blog.naver.com/legacy/77"))));

    Map<String, Object> after = siteToolService.previewScore(id);
    assertThat((Integer) after.get("totalScore"))
        .isGreaterThanOrEqualTo((Integer) before.get("totalScore"));
    assertThat(((Number) after.get("evidenceCount")).intValue())
        .isGreaterThan(beforeEvidence);
  }
}
