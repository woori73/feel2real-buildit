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
import org.springframework.web.server.ResponseStatusException;

/**
 * PR-A 아카이브 우선 파이프라인 E2E.
 *   · 저장 = 정보 구조화. 최소 게이트만 통과하면 RESEARCH_PENDING 으로 저장(검색 노출, 점수 제외).
 *   · 최소 게이트 실패(신원 미확인 / 명백한 범위 이탈 / 근거 URL 없음)만 422.
 *   · 점수화 / complete_region 선정은 EVALUATABLE 후보만.
 */
@SpringBootTest
@Transactional
class ChartArchivePipelineMySqlTest {

  @Autowired ChartSiteToolService siteToolService;
  @Autowired JdbcTemplate jdbcTemplate;

  private static final String PHILOSOPHY =
      "학생이 스스로 질문을 만들며 원리를 이해하도록 이끌고, 틀린 문제는 사고 과정을 다시 설계하는 재료로 삼는다.";
  private static final String CURRICULUM =
      "진단 평가로 취약 지점을 찾은 뒤 개념 강의를 하고, 학교별 기출 분석과 변형 문제로 적용한 다음 오답 노트를 반복한다.";

  private String freshRegion(String suffix) {
    String id = "test-archive-" + suffix + "-" + System.nanoTime();
    jdbcTemplate.update("""
        INSERT INTO chart_regions (region_id, province, city_district, status, created_at, updated_at)
        VALUES (?, '테스트도', ?, 'UNRESEARCHED', NOW(6), NOW(6))
        """, id, "테스트시 " + suffix);
    return id;
  }

  private Map<String, Object> save(String regionId, String name, String philosophy, String curriculum,
      boolean offline, boolean currentYear, boolean identity, boolean exclusionFree, List<EvidenceRequest> evidence) {
    return siteToolService.saveDraft(new DraftRequest(regionId, name, "원장", name + "영어", "테스트도", "테스트시",
        name + " 교육 정체성", philosophy, curriculum, "질문식 토론 수업", "2026년 봄학기 공지",
        List.of("자체 커리큘럼 운영"), List.of("온라인 정리 보강"), null, null,
        "https://example.com/" + name, null, null, null, "공개 콘텐츠에서 개인 교육활동 확인",
        offline, currentYear, identity, exclusionFree, List.of(), evidence));
  }

  private List<EvidenceRequest> fullEvidence(String name) {
    return List.of(
        new EvidenceRequest("커리큘럼 소개", "자체 독해 커리큘럼과 수업 단계 설명", "커리큘럼",
            "https://example.com/" + name + "/curriculum", LocalDate.now(), "CURRICULUM", true),
        new EvidenceRequest("오프라인 수업", "현재 학생을 지도하는 정규 수업 일정", "수업",
            "https://example.com/" + name + "/class", LocalDate.now(), "OFFLINE_ACTIVITY", true));
  }

  private String eligibilityStatus(long candidateId) {
    return jdbcTemplate.queryForObject(
        "SELECT eligibility_status FROM chart_candidates WHERE candidate_id=?", String.class, candidateId);
  }

  @Test
  void sparseDraft_isSavedAsResearchPending_notRejected() {
    String region = freshRegion("pending");
    Map<String, Object> result = save(region, "보류원장", null, null, false, true, true, true, fullEvidence("보류원장"));

    assertThat(result).containsEntry("eligibilityStatus", "RESEARCH_PENDING")
        .containsEntry("evaluationHold", true);
    long id = ((Number) result.get("candidateId")).longValue();
    assertThat(eligibilityStatus(id)).isEqualTo("RESEARCH_PENDING");
    // 아카이브에 실제로 남아 있고, 판정 사유가 보존된다
    assertThat(jdbcTemplate.queryForObject(
        "SELECT eligibility_reasons_json FROM chart_candidates WHERE candidate_id=?", String.class, id))
        .contains("교육철학");
  }

  @Test
  void fullDraft_isSavedAsEvaluatable() {
    String region = freshRegion("eval");
    Map<String, Object> result = save(region, "평가원장", PHILOSOPHY, CURRICULUM, true, true, true, true, fullEvidence("평가원장"));
    assertThat(result).containsEntry("eligibilityStatus", "EVALUATABLE").containsEntry("evaluationHold", false);
    assertThat(eligibilityStatus(((Number) result.get("candidateId")).longValue())).isEqualTo("EVALUATABLE");
  }

  @Test
  void draft_isRejected_whenClearlyOutOfScope() {
    String region = freshRegion("reject");
    assertThatThrownBy(() -> save(region, "범위이탈", PHILOSOPHY, CURRICULUM, true, true, true, false, fullEvidence("범위이탈")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("저장 최소 조건 미달");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_candidates WHERE zone_id=?", Integer.class, region)).isZero();
  }

  @Test
  void researchPendingCandidate_cannotBeScoredOrSelected() {
    String region = freshRegion("noscore");
    long pendingId = ((Number) save(region, "보류점수", null, CURRICULUM, false, true, true, true, fullEvidence("보류점수"))
        .get("candidateId")).longValue();

    assertThatThrownBy(() -> siteToolService.previewScore(pendingId))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("EVALUATABLE");

    siteToolService.claimRegion(region);
    assertThatThrownBy(() -> siteToolService.completeRegion(region, List.of(pendingId), 1, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("평가 보류");
  }

  @Test
  void draft_withInvalidFirstEvidence_usesNextValidEvidence_andSkipsInvalid() {
    // 최소 게이트(assess)와 saveDraft 저장 판정이 일치: evidence[0] 이 잘못돼도 뒤의 정상 evidence 를 대표로 쓴다.
    String region = freshRegion("mixedev");
    Map<String, Object> result = save(region, "혼합근거", PHILOSOPHY, CURRICULUM, true, true, true, true, List.of(
        new EvidenceRequest("커리큘럼", "자체 커리큘럼 설명", "커리큘럼", "not-a-url",
            LocalDate.now(), "CURRICULUM", true),
        new EvidenceRequest("오프라인 수업", "현재 학생을 지도하는 정규 수업", "수업",
            "https://example.com/mixed-valid", LocalDate.now(), "OFFLINE_ACTIVITY", true)));

    assertThat(result).containsEntry("eligibilityStatus", "EVALUATABLE").containsEntry("skippedEvidence", 1);
    long id = ((Number) result.get("candidateId")).longValue();
    // invalid URL evidence 는 건너뛰고 유효 evidence 1건만 활동으로 저장한다(보정·추측 없음).
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_candidate_activities WHERE candidate_id=?", Integer.class, id)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT source_url FROM chart_candidates WHERE candidate_id=?", String.class, id))
        .isEqualTo("https://example.com/mixed-valid");
  }

  @Test
  void evaluatableCandidate_isScoredSelectedAndPublished() {
    String region = freshRegion("publish");
    long id = ((Number) save(region, "발행원장", PHILOSOPHY, CURRICULUM, true, true, true, true, fullEvidence("발행원장"))
        .get("candidateId")).longValue();

    Map<String, Object> preview = siteToolService.previewScore(id);
    assertThat(((Number) preview.get("totalScore")).intValue()).isGreaterThan(0);

    siteToolService.claimRegion(region);
    Map<String, Object> completed = siteToolService.completeRegion(region, List.of(id), 1, null);
    assertThat(completed).containsEntry("status", "RESEARCHED");
    assertThat((Boolean) completed.get("publicationChanged")).isTrue();
    Long pubId = ((Number) completed.get("publicationId")).longValue();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM chart_publication_entries WHERE publication_id=? AND candidate_id=?",
        Integer.class, pubId, id)).isEqualTo(1);
  }
}
