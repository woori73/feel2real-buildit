package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorPage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공개 교육자 검색/상세 E2E (실제 MySQL). CI(build-check)에서는 실행하지 않는다.
 *   · 공개 대상 = EVALUATABLE + RESEARCH_PENDING, 미제외.
 *   · excluded / eligibility NULL 후보는 검색·상세에 노출되지 않는다.
 *   · 응답에 Score·순위·개인정보·내부 판정 필드가 없다.
 */
@SpringBootTest
@Transactional
class ChartEducatorSearchMySqlTest {

  @Autowired ChartEducatorSearchService service;
  @Autowired JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private String tag;

  @BeforeEach
  void setUp() {
    tag = "srch" + System.nanoTime();
  }

  private long insertCandidate(String name, String province, String city, String eligibility,
      boolean excluded, String academy, String identity, String philosophy) {
    String personKey = tag + "|" + name;
    jdbcTemplate.update("""
        INSERT INTO chart_candidates
        (zone_id, person_key, name, role, academy_name, activity_region, activity_province,
         activity_city_district, content_type, content_summary, identity_summary,
         education_philosophy_summary, curriculum_summary, distinctive_teaching_summary,
         recent_activity_summary, strengths_json, improvement_suggestions_json,
         eligibility_status, excluded_from_chart, potential_signals_json, eligibility_reasons_json,
         research_source, criteria_version, source_url, source_url_hash, source_title,
         discovery_reason, deep_search_result, exclusion_check, verification_status, status,
         google_query, official_homepage, discovered_at, created_at, updated_at)
        VALUES (?, ?, ?, '원장', ?, ?, ?, ?, '글', '공개 콘텐츠 요약', ?,
                ?, '진단→개념→적용→오답 반복', '토론식 수업', '2026년 봄학기 신규반',
                '["자체 커리큘럼 운영"]', '["온라인 정리 보강"]',
                ?, ?, '[]', '["내부 판정 사유 - 노출 금지"]',
                'CHATGPT_SITE_TOOL', 'buildit-chart-v1', ?, ?, '근거 제목',
                '블로그 수업 후기에서 발견', 'INTERNAL_DEEP_SEARCH_NOTE_DO_NOT_EXPOSE',
                'INTERNAL_EXCLUSION_MEMO_DO_NOT_EXPOSE', 'VERIFIED', 'DISCOVERED',
                'INTERNAL_GOOGLE_QUERY_DO_NOT_EXPOSE', 'https://example.com/home', NOW(6), NOW(6), NOW(6))
        """,
        tag, personKey, name, academy, province + " " + city + " 어딘가", province, city, identity,
        philosophy, eligibility, excluded ? 1 : 0,
        "https://example.com/" + tag + "/" + name, tag + name);
    Long id = jdbcTemplate.queryForObject("SELECT candidate_id FROM chart_candidates WHERE person_key=?",
        Long.class, personKey);
    jdbcTemplate.update("""
        INSERT INTO chart_candidate_activities
        (candidate_id, content_type, content_summary, activity_date, evidence_kind,
         educationally_informative, researched_at, source_url, discovered_at, created_at, updated_at)
        VALUES (?, '수업', '현장 정규 수업', CURDATE(), 'OFFLINE_ACTIVITY', b'1', NOW(6),
                ?, NOW(6), NOW(6), NOW(6))
        """, id, "https://example.com/" + tag + "/" + name + "/ev");
    return id;
  }

  @Test
  void search_returnsEvaluatableAndResearchPending_butNotExcludedOrNullEligibility() {
    insertCandidate("가교육", "테스트도", "가나시", "EVALUATABLE", false, "가교육영어", "독해 전문", "질문 중심 철학입니다 하나 둘 셋");
    insertCandidate("나교육", "테스트도", "가나시", "RESEARCH_PENDING", false, "나교육영어", "문법 전문", null);
    insertCandidate("다제외", "테스트도", "가나시", "EVALUATABLE", true, "다제외영어", "제외 대상", "철학");
    insertCandidate("라널", "테스트도", "가나시", null, false, "라널영어", "eligibility 없음", "철학");

    ChartPublicEducatorPage page = service.search("테스트도", "가나시", null, null, 0, 20);

    List<String> names = page.content().stream().map(e -> e.name()).toList();
    assertThat(names).containsExactlyInAnyOrder("가교육", "나교육");
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content()).anySatisfy(e -> {
      assertThat(e.eligibilityStatus()).isIn("EVALUATABLE", "RESEARCH_PENDING");
    });
    assertThat(page.content()).filteredOn(e -> e.name().equals("나교육"))
        .singleElement().satisfies(e -> assertThat(e.researchPending()).isTrue());
  }

  @Test
  void search_filtersByStatusProvinceCityAndQuery() {
    insertCandidate("김서울", "서울도", "서울시", "EVALUATABLE", false, "김서울영어학원", "내신 전문", "철학 문장 하나 둘 셋 넷");
    insertCandidate("이부산", "부산도", "부산시", "RESEARCH_PENDING", false, "이부산영어", "회화 전문", null);

    assertThat(service.search(null, null, null, "EVALUATABLE", 0, 20).content())
        .allSatisfy(e -> assertThat(e.eligibilityStatus()).isEqualTo("EVALUATABLE"))
        .anySatisfy(e -> assertThat(e.name()).isEqualTo("김서울"));

    assertThat(service.search("부산도", null, null, null, 0, 20).content())
        .extracting(e -> e.name()).containsExactly("이부산");

    assertThat(service.search(null, "서울시", null, null, 0, 20).content())
        .extracting(e -> e.name()).containsExactly("김서울");

    assertThat(service.search(null, null, "김서울영어학원", null, 0, 20).content())
        .extracting(e -> e.name()).containsExactly("김서울");
  }

  @Test
  void search_rejectsUnknownStatus() {
    assertThatThrownBy(() -> service.search(null, null, null, "REJECTED", 0, 20))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void search_paginates() {
    for (int i = 0; i < 3; i++) {
      insertCandidate("페이지" + i, "페이지도", "페이지시", "EVALUATABLE", false, "학원" + i, "정체성" + i, "철학 문장 " + i + " 하나 둘");
    }
    ChartPublicEducatorPage p0 = service.search("페이지도", "페이지시", null, null, 0, 2);
    ChartPublicEducatorPage p1 = service.search("페이지도", "페이지시", null, null, 1, 2);
    assertThat(p0.content()).hasSize(2);
    assertThat(p1.content()).hasSize(1);
    assertThat(p0.totalElements()).isEqualTo(3);
    assertThat(p0.totalPages()).isEqualTo(2);
  }

  @Test
  void getById_returnsDetailForResearchPending_withoutScoreOrPrivateData() throws Exception {
    long id = insertCandidate("상세보류", "상세도", "상세시", "RESEARCH_PENDING", false, "상세보류영어", "독해", null);

    ChartPublicEducatorDetail detail = service.getById(id);

    assertThat(detail.id()).isEqualTo(id);
    assertThat(detail.name()).isEqualTo("상세보류");
    assertThat(detail.researchPending()).isTrue();
    assertThat(detail.evidence()).isNotEmpty();
    assertThat(detail.evidence().get(0).sourceUrl()).contains("example.com");
    assertThat(detail.officialChannels()).isNotNull();

    String json = objectMapper.writeValueAsString(detail);
    assertThat(json)
        .doesNotContain("내부 판정 사유 - 노출 금지")
        .doesNotContain("INTERNAL_DEEP_SEARCH_NOTE_DO_NOT_EXPOSE")
        .doesNotContain("INTERNAL_EXCLUSION_MEMO_DO_NOT_EXPOSE")
        .doesNotContain("INTERNAL_GOOGLE_QUERY_DO_NOT_EXPOSE")
        .doesNotContain("person_key").doesNotContain("personKey")
        .doesNotContain("eligibility_reasons").doesNotContain("eligibilityReasons")
        .doesNotContain("deepSearch").doesNotContain("googleQuery")
        .doesNotContain("\"score\"").doesNotContain("buildItScore").doesNotContain("\"rank\"");
  }

  @Test
  void getById_404ForExcludedOrNullEligibilityOrMissing() {
    long excluded = insertCandidate("숨김", "x도", "x시", "EVALUATABLE", true, "숨김영어", "정체성", "철학");
    long nullElig = insertCandidate("무상태", "x도", "x시", null, false, "무상태영어", "정체성", "철학");

    assertThatThrownBy(() -> service.getById(excluded)).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.getById(nullElig)).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.getById(999_999_999L)).isInstanceOf(ResponseStatusException.class);
  }
}
