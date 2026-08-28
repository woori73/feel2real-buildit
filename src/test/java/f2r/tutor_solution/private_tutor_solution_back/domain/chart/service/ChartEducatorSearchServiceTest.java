package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorSummary;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공개 교육자 검색/상세 — 순수 로직(입력 정규화, LIKE 이스케이프, row → DTO 매핑, 개인정보 비노출).
 * DB 왕복은 {@code ChartEducatorSearchMySqlTest} 에서 검증한다.
 */
class ChartEducatorSearchServiceTest {

  private final ChartEducatorSearchService service = new ChartEducatorSearchService(mock(JdbcTemplate.class));

  // ---- status 파라미터 ----

  @Test
  void resolveStatuses_defaultsToBothPublicStatuses() {
    assertThat(service.resolveStatuses(null)).containsExactly("EVALUATABLE", "RESEARCH_PENDING");
    assertThat(service.resolveStatuses("  ")).containsExactly("EVALUATABLE", "RESEARCH_PENDING");
  }

  @Test
  void resolveStatuses_acceptsEitherPublicStatusCaseInsensitively() {
    assertThat(service.resolveStatuses("evaluatable")).containsExactly("EVALUATABLE");
    assertThat(service.resolveStatuses("RESEARCH_PENDING")).containsExactly("RESEARCH_PENDING");
  }

  @Test
  void resolveStatuses_rejectsUnknownOrInternalStatus() {
    assertThatThrownBy(() -> service.resolveStatuses("REJECTED"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.resolveStatuses("ELIGIBLE"))
        .isInstanceOf(ResponseStatusException.class);
  }

  // ---- pagination ----

  @Test
  void clampSize_appliesDefaultAndCeiling() {
    assertThat(service.clampSize(null)).isEqualTo(20);
    assertThat(service.clampSize(0)).isEqualTo(20);
    assertThat(service.clampSize(-5)).isEqualTo(20);
    assertThat(service.clampSize(15)).isEqualTo(15);
    assertThat(service.clampSize(9999)).isEqualTo(100);
  }

  @Test
  void clampPage_neverNegative() {
    assertThat(service.clampPage(null)).isZero();
    assertThat(service.clampPage(-2)).isZero();
    assertThat(service.clampPage(4)).isEqualTo(4);
  }

  // ---- LIKE 이스케이프 ----

  @Test
  void likeArg_lowercasesWrapsAndEscapesMetacharacters() {
    assertThat(ChartEducatorSearchService.likeArg("Kim")).isEqualTo("%kim%");
    // % _ ! 는 ESCAPE '!' 기준으로 리터럴화된다
    assertThat(ChartEducatorSearchService.likeArg("a%b_c!d")).isEqualTo("%a!%b!_c!!d%");
  }

  // ---- row → summary ----

  @Test
  void toSummary_mapsEducatorCentricFieldsAndBlanksToNull() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "EVALUATABLE");
    row.put("academy_name", "   ");

    ChartPublicEducatorSummary s = service.toSummary(row);

    assertThat(s.id()).isEqualTo(7L);
    assertThat(s.name()).isEqualTo("홍길동");
    assertThat(s.role()).isEqualTo("원장");
    assertThat(s.academyName()).isNull();
    assertThat(s.province()).isEqualTo("서울특별시");
    assertThat(s.cityDistrict()).isEqualTo("강남구");
    assertThat(s.identitySummary()).isEqualTo("독해 중심 교육자");
    assertThat(s.educationPhilosophy()).isEqualTo("질문으로 원리를 세운다");
    assertThat(s.eligibilityStatus()).isEqualTo("EVALUATABLE");
    assertThat(s.researchPending()).isFalse();
  }

  @Test
  void toSummary_flagsResearchPending() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "RESEARCH_PENDING");
    assertThat(service.toSummary(row).researchPending()).isTrue();
  }

  // ---- row → detail ----

  @Test
  void toDetail_mapsFactsFromDbOnly_noFabrication() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "EVALUATABLE");
    row.put("curriculum", "진단→개념→적용→오답");
    row.put("distinctive_teaching", "토론식 수업");
    row.put("recent_activity_summary", "2026년 봄 신규반");
    row.put("strengths_json", "[\"자체 커리큘럼\",\"콘텐츠 지속\"]");
    row.put("improvement_suggestions_json", "[]");
    row.put("growth_advice", "커리큘럼을 온라인에 정리");
    row.put("official_homepage", "https://example.com/home");
    row.put("discovery_reason", "블로그에서 발견");

    List<Map<String, Object>> activities = List.of(
        activityRow("수업", "정규 수업", "https://example.com/class", LocalDate.of(2026, 3, 1), "OFFLINE_ACTIVITY",
            LocalDateTime.of(2026, 3, 2, 9, 0)),
        activityRow("칼럼", "교육 철학 글", "https://example.com/col", LocalDate.of(2026, 1, 1), "PHILOSOPHY",
            LocalDateTime.of(2026, 1, 5, 9, 0)));

    ChartPublicEducatorDetail d = service.toDetail(row, activities);

    assertThat(d.curriculum()).isEqualTo("진단→개념→적용→오답");
    assertThat(d.distinctiveTeaching()).isEqualTo("토론식 수업");
    assertThat(d.strengths()).containsExactly("자체 커리큘럼", "콘텐츠 지속");
    assertThat(d.improvementSuggestions()).containsExactly("커리큘럼을 온라인에 정리"); // growth_advice fallback
    assertThat(d.officialChannels()).isNotNull();
    assertThat(d.officialChannels().homepage()).isEqualTo("https://example.com/home");
    assertThat(d.evidence()).hasSize(2);
    assertThat(d.evidence().get(0).sourceUrl()).isEqualTo("https://example.com/class");
    assertThat(d.lastResearchedAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 0)); // 최신 activity researched_at
    assertThat(d.researchPending()).isFalse();
  }

  @Test
  void toDetail_researchPending_marksStatusAndDisclaimer_noScoreFields() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "RESEARCH_PENDING");

    ChartPublicEducatorDetail d = service.toDetail(row, List.of());

    assertThat(d.researchPending()).isTrue();
    assertThat(d.eligibilityStatus()).isEqualTo("RESEARCH_PENDING");
    assertThat(d.disclaimer()).contains("RESEARCH_PENDING").contains("TOP100");
    // 상세 DTO 에 Score/순위 필드가 애초에 존재하지 않는다
    Set<String> fields = recordFieldNames(ChartPublicEducatorDetail.class);
    assertThat(fields).doesNotContain("score", "buildItScore", "totalScore", "rank", "chartRank", "scoreBreakdown");
  }

  @Test
  void detailAndSummaryDtos_neverExposeInternalOrPrivateFields() {
    Set<String> forbidden = Set.of(
        "personKey", "person_key", "sourceUrlHash", "eligibilityReasonsJson", "eligibility_reasons_json",
        "deepSearchResult", "exclusionCheck", "googleQuery", "legacyMetadataJson", "excludedReason",
        "phone", "email", "address", "createdBy", "updatedBy", "verificationStatus",
        "score", "buildItScore", "totalScore", "rank", "chartRank");
    assertThat(recordFieldNames(ChartPublicEducatorDetail.class)).doesNotContainAnyElementsOf(forbidden);
    assertThat(recordFieldNames(ChartPublicEducatorSummary.class)).doesNotContainAnyElementsOf(forbidden);
  }

  @Test
  void disclaimer_distinguishesPendingFromEvaluatable() {
    assertThat(ChartEducatorSearchService.disclaimer(true)).contains("RESEARCH_PENDING");
    assertThat(ChartEducatorSearchService.disclaimer(false)).doesNotContain("RESEARCH_PENDING");
    assertThat(ChartEducatorSearchService.disclaimer(false)).contains("TOP100");
  }

  // ---- helpers ----

  private static Set<String> recordFieldNames(Class<?> record) {
    return java.util.Arrays.stream(record.getRecordComponents())
        .map(RecordComponent::getName).collect(Collectors.toSet());
  }

  private static Map<String, Object> baseRow() {
    Map<String, Object> row = new HashMap<>();
    row.put("candidate_id", 7L);
    row.put("name", "홍길동");
    row.put("role", "원장");
    row.put("academy_name", "길동영어");
    row.put("activity_region", "서울 강남구 대치동");
    row.put("activity_province", "서울특별시");
    row.put("activity_city_district", "강남구");
    row.put("identity_summary", "독해 중심 교육자");
    row.put("education_philosophy", "질문으로 원리를 세운다");
    row.put("profile_image_url", "https://example.com/p.jpg");
    row.put("discovered_at", LocalDateTime.of(2026, 2, 1, 0, 0));
    row.put("updated_at", LocalDateTime.of(2026, 2, 10, 0, 0));
    return row;
  }

  private static Map<String, Object> activityRow(String type, String summary, String url, LocalDate date,
      String kind, LocalDateTime researchedAt) {
    Map<String, Object> a = new HashMap<>();
    a.put("content_type", type);
    a.put("content_summary", summary);
    a.put("source_url", url);
    a.put("activity_date", date);
    a.put("evidence_kind", kind);
    a.put("researched_at", researchedAt);
    return a;
  }
}
