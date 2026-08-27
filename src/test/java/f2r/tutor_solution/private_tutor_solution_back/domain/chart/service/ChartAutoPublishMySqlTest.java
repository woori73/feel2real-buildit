package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 전국 Living Archive 자동 발행 E2E.
 *   claim → save_research_drafts → complete → 서버 자동 publication → RESEARCHED → 다음 지역.
 *   publication 은 특정 지역 결과가 아니라 그 시점 전국 유효 교육자 누적 snapshot 이다.
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

  private void researchOne(String regionId, String name, String academy) {
    siteToolService.saveDraft(new DraftRequest(regionId, name, "원장", academy, "테스트도", "테스트시 " + regionId.substring(0, 4),
        name + " 교육 정체성", PHILOSOPHY, CURRICULUM, "질문식 토론 수업", "2026년 봄학기 신규반 개강 공지",
        List.of("자체 커리큘럼 운영", "수업 콘텐츠 지속 공개"), List.of("대표 커리큘럼을 온라인에 더 명확히 정리"),
        null, null, "https://example.com/" + name, null, null, null, "공개 콘텐츠에서 개인 교육활동과 커리큘럼 확인",
        true, true, true, true, List.of(),
        List.of(
            new EvidenceRequest("커리큘럼 소개", "자체 독해 커리큘럼과 수업 단계 설명", "커리큘럼", "https://example.com/" + name + "/curriculum",
                LocalDate.now(), "CURRICULUM", true),
            new EvidenceRequest("오프라인 수업", "현재 학생을 지도하는 정규 수업 일정", "수업", "https://example.com/" + name + "/class",
                LocalDate.now(), "OFFLINE_ACTIVITY", true))));
  }

  @Test
  void completeRegion_autoPublishesSelectedEducatorsAndMarksResearched() {
    // given
    String region = freshRegion("a");
    siteToolService.claimRegion(region);
    researchOne(region, "김일", "일영어학원");
    researchOne(region, "이이", "이영어학원");

    // when
    Map<String, Object> result = siteToolService.completeRegion(region, 2, 0, "테스트 조사 완료");

    // then: 지역 완료 + publication 생성
    assertThat(result).containsEntry("status", "RESEARCHED");
    assertThat((Boolean) result.get("publicationChanged")).isTrue();
    assertThat(jdbcTemplate.queryForObject("SELECT status FROM chart_regions WHERE region_id=?", String.class, region))
        .isEqualTo("RESEARCHED");
    Long publicationId = ((Number) result.get("publicationId")).longValue();
    assertThat(jdbcTemplate.queryForObject("SELECT status FROM chart_publications WHERE publication_id=?", String.class, publicationId))
        .isEqualTo("PUBLISHED");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries WHERE publication_id=? AND candidate_id IN (SELECT candidate_id FROM chart_candidates WHERE zone_id=?)",
        Integer.class, publicationId, region)).isEqualTo(2);

    // then: /api/chart/publication/current 이 Living Archive 필드를 반환
    Map<String, Object> current = publicService.currentPublication();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> ranked = (List<Map<String, Object>>) current.get("rankedTop100");
    assertThat(ranked).isNotEmpty();
    assertThat(ranked.get(0)).containsKeys("name", "role", "buildItScore", "educationPhilosophy", "curriculum",
        "strengths", "improvementSuggestions", "activities");
    assertThat(current.get("updatedLabel").toString()).startsWith("최근 업데이트 ");
  }

  @Test
  void secondRegion_producesCumulativeSnapshotWithPreviousRankAndSupersedesOld() {
    // given: 첫 지역 발행
    String regionA = freshRegion("b");
    siteToolService.claimRegion(regionA);
    researchOne(regionA, "박삼", "삼영어학원");
    Map<String, Object> first = siteToolService.completeRegion(regionA, 1, 0, null);
    Long firstPubId = ((Number) first.get("publicationId")).longValue();

    // when: 두 번째 지역 발행
    String regionB = freshRegion("c");
    siteToolService.claimRegion(regionB);
    researchOne(regionB, "최사", "사영어학원");
    Map<String, Object> second = siteToolService.completeRegion(regionB, 1, 0, null);
    Long secondPubId = ((Number) second.get("publicationId")).longValue();

    // then: 이전 publication 은 SUPERSEDED, 새 publication 이 두 지역 교육자 모두 누적
    assertThat(jdbcTemplate.queryForObject("SELECT status FROM chart_publications WHERE publication_id=?", String.class, firstPubId))
        .isEqualTo("SUPERSEDED");
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_publication_entries WHERE publication_id=?", Integer.class, secondPubId))
        .isGreaterThanOrEqualTo(2);
    // 첫 지역 교육자는 previous_rank 를 물려받는다
    Integer withPrev = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM chart_publication_entries
         WHERE publication_id=? AND previous_rank IS NOT NULL
        """, Integer.class, secondPubId);
    assertThat(withPrev).isGreaterThanOrEqualTo(1);
    // current publication 은 정확히 하나
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_publications WHERE status='PUBLISHED'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void completeRegion_withNoSelection_marksResearchedAndKeepsCurrentPublication() {
    // given: 후보를 저장하지 않은 지역
    String region = freshRegion("d");
    siteToolService.claimRegion(region);
    Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_publications", Integer.class);

    // when
    Map<String, Object> result = siteToolService.completeRegion(region, 0, 3, "적합 교육자 없음");

    // then: 0명도 정상 완료, 새 publication 없음
    assertThat(result).containsEntry("status", "RESEARCHED").containsEntry("selectedCount", 0);
    assertThat((Boolean) result.get("publicationChanged")).isFalse();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_publications", Integer.class)).isEqualTo(before);
  }

  @Test
  void completeRegion_rejectsNonInProgressRegion() {
    String region = freshRegion("e");
    // claim 하지 않음 → UNRESEARCHED
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> siteToolService.completeRegion(region, 0, 0, null))
        .hasMessageContaining("IN_PROGRESS");
  }
}
