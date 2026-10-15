package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicOfficialChannels;
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
  // 클래스패스의 Jackson 모듈(jsr310 등)을 등록해 LocalDateTime 등을 직렬화할 수 있게 한다.
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  // ---- 직렬화: officialChannels 는 record component 만 노출 (hotfix: "empty" 필드 제거) ----

  @Test
  void officialChannels_serializesOnlyRecordComponents_noEmptyOrHelperField() throws Exception {
    ChartPublicOfficialChannels ch = new ChartPublicOfficialChannels(
        "https://home.example.com", null, "https://youtube.example.com/@x", null);

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(ch));

    assertThat(iterableFieldNames(json)).containsExactlyInAnyOrder("homepage", "blog", "youtube", "social");
    assertThat(json.has("empty")).isFalse();
    assertThat(json.has("hasNoChannels")).isFalse();
    assertThat(json.get("homepage").asText()).isEqualTo("https://home.example.com");
    assertThat(json.get("blog").isNull()).isTrue();
  }

  @Test
  void detailJson_officialChannels_hasNoEmptyField_andNoScoreOrRank() throws Exception {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "EVALUATABLE");
    row.put("official_homepage", "https://home.example.com");
    row.put("official_youtube", "https://youtube.example.com/@x");

    ChartPublicEducatorDetail d = service.toDetail(row, List.of());
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(d));

    assertThat(json.get("officialChannels").has("empty")).isFalse();
    assertThat(iterableFieldNames(json.get("officialChannels")))
        .containsExactlyInAnyOrder("homepage", "blog", "youtube", "social");
    // Score/rank 계열 키는 상세 JSON 어디에도 없다
    for (String forbidden : List.of("score", "buildItScore", "totalScore", "rank", "chartRank", "empty")) {
      assertThat(json.findValue(forbidden)).as("forbidden key: " + forbidden).isNull();
    }
  }

  private static Set<String> iterableFieldNames(JsonNode node) {
    Set<String> names = new java.util.HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

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

  // ---- S1: evidence URL read-side 방어 ----

  @Test
  void toDetail_excludesEvidenceWithInvalidOrBlankOrNonHttpUrl() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "EVALUATABLE");
    List<Map<String, Object>> acts = List.of(
        activityRow("수업", "정규 수업", "https://example.com/ok", LocalDate.of(2026, 3, 1), "OFFLINE_ACTIVITY",
            LocalDateTime.of(2026, 3, 2, 9, 0)),
        activityRow("칼럼", "글", "not-a-url", LocalDate.of(2026, 2, 1), "PHILOSOPHY", null),
        activityRow("영상", "영상", "   ", LocalDate.of(2026, 1, 1), "EDUCATIONAL_CONTENT", null),
        activityRow("메모", "x", "ftp://example.com/f", LocalDate.of(2026, 1, 1), "MATERIAL", null),
        activityRow("스크립트", "x", "javascript:alert(1)", LocalDate.of(2026, 1, 1), "MATERIAL", null));

    ChartPublicEducatorDetail d = service.toDetail(row, acts);

    assertThat(d.evidence()).hasSize(1);
    assertThat(d.evidence().get(0).sourceUrl()).isEqualTo("https://example.com/ok");
  }

  @Test
  void toDetail_allEvidenceUrlsInvalid_yieldsEmptyEvidence_butStillUsesResearchedAt() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "RESEARCH_PENDING");
    List<Map<String, Object>> acts = List.of(
        activityRow("x", "x", "bad", LocalDate.of(2026, 1, 1), "MATERIAL", LocalDateTime.of(2026, 5, 1, 0, 0)));

    ChartPublicEducatorDetail d = service.toDetail(row, acts);

    assertThat(d.evidence()).isEmpty();
    assertThat(d.lastResearchedAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 0, 0));
  }

  @Test
  void isPublicHttpUrl_acceptsOnlyHttpAndHttpsWithHost() {
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("https://a.example.com/x")).isTrue();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("http://a.example.com")).isTrue();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("HTTPS://A.EXAMPLE.com")).isTrue();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("ftp://a.example.com")).isFalse();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("javascript:alert(1)")).isFalse();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("not-a-url")).isFalse();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("has space")).isFalse();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("  ")).isFalse();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl(null)).isFalse();
    assertThat(ChartEducatorSearchService.isPublicHttpUrl("https:///nohost")).isFalse();
  }

  // ---- S2: official channel URL read-side 방어 ----

  @Test
  void toDetail_invalidOfficialChannelUrlBecomesNull() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "EVALUATABLE");
    row.put("official_homepage", "https://ok.example.com");
    row.put("official_blog", "javascript:alert(1)");
    row.put("official_youtube", "   ");
    row.put("official_social", "not a url");

    ChartPublicEducatorDetail d = service.toDetail(row, List.of());

    assertThat(d.officialChannels()).isNotNull();
    assertThat(d.officialChannels().homepage()).isEqualTo("https://ok.example.com");
    assertThat(d.officialChannels().blog()).isNull();
    assertThat(d.officialChannels().youtube()).isNull();
    assertThat(d.officialChannels().social()).isNull();
  }

  @Test
  void toDetail_allOfficialChannelsInvalid_yieldsNullChannels() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "EVALUATABLE");
    row.put("official_homepage", "ftp://x");
    row.put("official_blog", "bad");
    row.put("official_youtube", "");
    // official_social 미설정

    assertThat(service.toDetail(row, List.of()).officialChannels()).isNull();
  }

  // ---- S3: offset long 계산 / 매우 큰 page ----

  @Test
  void offset_isComputedInLongSpace_noIntOverflow() {
    assertThat(ChartEducatorSearchService.offset(Integer.MAX_VALUE, 100))
        .isEqualTo((long) Integer.MAX_VALUE * 100)
        .isGreaterThan((long) Integer.MAX_VALUE);
    assertThat(ChartEducatorSearchService.offset(0, 20)).isZero();
    assertThat(ChartEducatorSearchService.offset(3, 20)).isEqualTo(60L);
  }

  @Test
  void search_hugePageRequest_returnsEmptyContentWithoutOverflowOrError() {
    // bare mock: COUNT(*) → null → totalElements 0 → content 쿼리 미실행, 예외 없음
    ChartPublicEducatorPage page = service.search(null, null, null, null, Integer.MAX_VALUE, 100);
    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  // ---- S4: malformed json / totalElements=0 / size 경계 ----

  @Test
  void toDetail_malformedStrengthsAndImprovementJson_yieldEmptyLists_noException() {
    Map<String, Object> row = baseRow();
    row.put("eligibility_status", "EVALUATABLE");
    row.put("strengths_json", "{ not valid json ]");
    row.put("improvement_suggestions_json", "also broken");
    row.put("growth_advice", null);

    ChartPublicEducatorDetail d = service.toDetail(row, List.of());

    assertThat(d.strengths()).isEmpty();
    assertThat(d.improvementSuggestions()).isEmpty();
  }

  @Test
  void page_of_zeroResults_isEmptyWithZeroPages() {
    ChartPublicEducatorPage p = ChartPublicEducatorPage.of(List.of(), 0L, 0, 20);
    assertThat(p.content()).isEmpty();
    assertThat(p.totalElements()).isZero();
    assertThat(p.totalPages()).isZero();
    assertThat(p.page()).isZero();
    assertThat(p.size()).isEqualTo(20);
  }

  @Test
  void clampSize_zeroToDefault_overCeilingToMax() {
    assertThat(service.clampSize(0)).isEqualTo(20);
    assertThat(service.clampSize(101)).isEqualTo(100);
    assertThat(service.clampSize(1_000_000)).isEqualTo(100);
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
