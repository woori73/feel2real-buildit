package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.DraftRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService.EvidenceRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전국 Living Archive 자동 발행 + complete_region 선정 범위 무결성.
 *   · complete_region 은 명시적으로 넘긴 selectedCandidateIds 만 이 지역 선정자로 인정한다.
 *   · save_research_drafts 로 저장만 한 후보, 다른 지역 후보는 절대 자동 포함되지 않는다.
 *   · 선정 0명도 정상 완료이며 기존 nationwide publication 인원을 바꾸지 않는다.
 */
@SpringBootTest
@Transactional
class ChartAutoPublishMySqlTest {

  @Autowired ChartSiteToolService siteToolService;
  @Autowired ChartPublicService publicService;
  @Autowired JdbcTemplate jdbcTemplate;

  private static final String PHILOSOPHY =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";
  private static final String CURRICULUM =
      "진단 평가로 취약 지점을 찾은 뒤 개념 강의를 하고, 학교별 기출 분석과 변형 문제로 적용한 다음 오답 노트를 반복한다.";

  private String freshRegion(String suffix) {
    String id = "test-autopub-" + suffix + "-" + System.nanoTime();
    jdbcTemplate.update("""
        INSERT INTO chart_regions (region_id, province, city_district, status, created_at, updated_at)
        VALUES (?, '테스트도', ?, 'UNRESEARCHED', NOW(6), NOW(6))
        """, id, "테스트시 " + suffix);
    return id;
  }

  private long researchOne(String regionId, String name, String academy) {
    Map<String, Object> saved = siteToolService.saveDraft(new DraftRequest(regionId, name, "원장", academy,
        "테스트도", "테스트시", name + " 교육 정체성", PHILOSOPHY, CURRICULUM, "질문식 토론 수업",
        "2026년 봄학기 신규반 개강 공지", List.of("자체 커리큘럼 운영", "수업 콘텐츠 지속 공개"),
        List.of("대표 커리큘럼을 온라인에 더 명확하게 정리"), null, null,
        "https://example.com/" + name, null, null, null, "공개 콘텐츠에서 개인 교육활동과 커리큘럼 확인",
        true, true, true, true, List.of(),
        List.of(
            new EvidenceRequest("커리큘럼 소개", "자체 독해 커리큘럼과 수업 단계 설명", "커리큘럼", "https://example.com/" + name + "/curriculum",
                LocalDate.now(), "CURRICULUM", true),
            new EvidenceRequest("오프라인 수업", "현재 학생을 지도하는 정규 수업 일정", "수업", "https://example.com/" + name + "/class",
                LocalDate.now(), "OFFLINE_ACTIVITY", true))));
    return ((Number) saved.get("candidateId")).longValue();
  }

  private int publishedCount() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_publications WHERE status='PUBLISHED'", Integer.class);
  }

  @Test
  void completeRegion_publishesOnlyExplicitlySelectedCandidates() {
    String region = freshRegion("a");
    siteToolService.claimRegion(region);
    long picked = researchOne(region, "김선정", "선정영어학원");
    long notPicked = researchOne(region, "이보류", "보류영어학원");   // draft 저장만 하고 선정하지 않음

    Map<String, Object> result = siteToolService.completeRegion(region, List.of(picked), 1, "1명 선정");

    assertThat(result).containsEntry("status", "RESEARCHED").containsEntry("selectedCount", 1);
    Long publicationId = ((Number) result.get("publicationId")).longValue();
    // 선정한 1명만 entry
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries WHERE publication_id=? AND candidate_id=?",
        Integer.class, publicationId, picked)).isEqualTo(1);
    // 저장만 한 후보는 절대 포함 안 됨
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries WHERE publication_id=? AND candidate_id=?",
        Integer.class, publicationId, notPicked)).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_score_snapshots WHERE candidate_id=?", Integer.class, notPicked)).isZero();
  }

  @Test
  void completeRegion_withZeroSelection_marksResearched_andKeepsPublicationUnchanged() {
    // 다른 지역엔 이미 선정자가 있고
    String regionA = freshRegion("b");
    siteToolService.claimRegion(regionA);
    long a1 = researchOne(regionA, "박기존", "기존영어학원");
    siteToolService.completeRegion(regionA, List.of(a1), 1, null);
    int publishedBefore = publishedCount();
    int entriesBefore = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries WHERE publication_id=(SELECT publication_id FROM chart_publications WHERE status='PUBLISHED' ORDER BY published_at DESC, publication_id DESC LIMIT 1)",
        Integer.class);

    // 현재 지역: draft 는 있지만 최종 선정 0명
    String regionB = freshRegion("c");
    siteToolService.claimRegion(regionB);
    researchOne(regionB, "최탈락", "탈락영어학원");

    Map<String, Object> result = siteToolService.completeRegion(regionB, List.of(), 0, "적합 교육자 0명");

    assertThat(result).containsEntry("status", "RESEARCHED").containsEntry("selectedCount", 0);
    assertThat((Boolean) result.get("publicationChanged")).isFalse();
    assertThat(publishedCount()).as("새 publication 없음").isEqualTo(publishedBefore);
    int entriesAfter = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries WHERE publication_id=(SELECT publication_id FROM chart_publications WHERE status='PUBLISHED' ORDER BY published_at DESC, publication_id DESC LIMIT 1)",
        Integer.class);
    assertThat(entriesAfter).as("기존 publication 인원 변화 없음").isEqualTo(entriesBefore);
    assertThat(jdbcTemplate.queryForObject("SELECT status FROM chart_regions WHERE region_id=?", String.class, regionB))
        .isEqualTo("RESEARCHED");
    // regionB draft 후보는 어떤 publication entry 에도 없음
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries pe JOIN chart_candidates c ON c.candidate_id=pe.candidate_id WHERE c.zone_id=?",
        Integer.class, regionB)).isZero();
  }

  @Test
  void consecutiveRegions_doNotMixCandidates() {
    String r1 = freshRegion("d");
    siteToolService.claimRegion(r1);
    long c1 = researchOne(r1, "가교육", "가영어학원");
    long c1b = researchOne(r1, "가보류", "가보류학원");
    siteToolService.completeRegion(r1, List.of(c1), 1, null);   // 1명 선정, c1b 는 보류

    String r2 = freshRegion("e");
    siteToolService.claimRegion(r2);
    long c2 = researchOne(r2, "나교육", "나영어학원");
    Map<String, Object> res2 = siteToolService.completeRegion(r2, List.of(c2), 1, null);
    Long pub2 = ((Number) res2.get("publicationId")).longValue();

    // 최신 publication: r1 선정자 + r2 선정자만, 보류/미선정 후보 없음
    List<Long> pub2Candidates = jdbcTemplate.queryForList(
        "SELECT candidate_id FROM chart_publication_entries WHERE publication_id=?", Long.class, pub2);
    assertThat(pub2Candidates).contains(c1, c2).doesNotContain(c1b);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_publications WHERE status='PUBLISHED'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void completeRegion_rejectsCandidateFromAnotherRegion() {
    String r1 = freshRegion("f");
    siteToolService.claimRegion(r1);
    long foreign = researchOne(r1, "타지역", "타지역학원");

    String r2 = freshRegion("g");
    siteToolService.claimRegion(r2);

    assertThatThrownBy(() -> siteToolService.completeRegion(r2, List.of(foreign), 1, null))
        .hasMessageContaining("다른 지역 소속");
  }

  @Test
  void completeRegion_rejectsNonInProgressRegion() {
    String region = freshRegion("h");
    assertThatThrownBy(() -> siteToolService.completeRegion(region, List.of(), 0, null))
        .hasMessageContaining("IN_PROGRESS");
  }
}
